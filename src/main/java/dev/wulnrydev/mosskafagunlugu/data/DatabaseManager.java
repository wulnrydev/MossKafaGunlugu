package dev.wulnrydev.mosskafagunlugu.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.wulnrydev.mosskafagunlugu.MossKafaGunlugu;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Tum veri tabani islemlerini asenkron olarak yoneten sinif.
 *
 * <p><b>Performans:</b> Hicbir sorgu ana thread'de calistirilmaz. Tum okuma/yazma
 * islemleri ayrilmis bir thread havuzunda {@link CompletableFuture} ile yurutulur.
 * HikariCP baglanti havuzu kullanilir. SQLite (varsayilan) ve MySQL desteklenir.</p>
 */
public final class DatabaseManager {

    private final MossKafaGunlugu plugin;
    private HikariDataSource dataSource;
    private ExecutorService executor;
    private boolean mysql;

    /** Aktif sezon numarasi (meta tablosundan yuklenir). */
    private final AtomicInteger currentSeason = new AtomicInteger(1);

    public DatabaseManager(MossKafaGunlugu plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Yasam dongusu
    // ------------------------------------------------------------------

    public void init() {
        FileConfiguration cfg = plugin.getConfig();
        String type = cfg.getString("database.type", "SQLITE").toUpperCase();
        this.mysql = type.equals("MYSQL");

        int poolSize = Math.max(1, cfg.getInt("database.pool-size", 6));
        this.executor = Executors.newFixedThreadPool(poolSize, namedThreadFactory());

        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("MossKafaGunlugu-Pool");
        hikari.setMaximumPoolSize(poolSize);

        if (mysql) {
            String host = cfg.getString("database.mysql.host", "localhost");
            int port = cfg.getInt("database.mysql.port", 3306);
            String database = cfg.getString("database.mysql.database", "mosskafagunlugu");
            boolean ssl = cfg.getBoolean("database.mysql.use-ssl", false);
            hikari.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=" + ssl + "&useUnicode=true&characterEncoding=utf8");
            hikari.setUsername(cfg.getString("database.mysql.username", "root"));
            hikari.setPassword(cfg.getString("database.mysql.password", ""));
        } else {
            File file = new File(plugin.getDataFolder(), cfg.getString("database.sqlite-file", "data.db"));
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("Veri klasoru olusturulamadi!");
            }
            hikari.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
            // SQLite tek yazici destekledigi icin havuzu kucuk tutmak gereksiz kilitlenmeyi onler.
            hikari.setMaximumPoolSize(Math.min(poolSize, 4));
            // Es zamanli yazimlarda "database is locked" (SQLITE_BUSY) hatasi yerine
            // baglanti en fazla 5 sn bekleyip tekrar dener.
            hikari.setConnectionInitSql("PRAGMA busy_timeout = 5000");
        }

        this.dataSource = new HikariDataSource(hikari);
        createTables();
        loadSeason();
        plugin.getLogger().info("Veri tabani baslatildi (" + type + "). Aktif sezon: " + currentSeason.get());
    }

    public void shutdown() {
        if (executor != null) {
            // Bekleyen yazimlarin (orn. son oldurmeler) tamamlanmasina firsat ver,
            // ardindan havuzu kapat. Boylece kapanista veri kaybi yasanmaz.
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private ThreadFactory namedThreadFactory() {
        AtomicInteger counter = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, "MossKafaGunlugu-DB-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    // ------------------------------------------------------------------
    //  Sema
    // ------------------------------------------------------------------

    private void createTables() {
        String killsTable = "CREATE TABLE IF NOT EXISTS kills ("
                + "killer_uuid VARCHAR(36) NOT NULL,"
                + "victim_uuid VARCHAR(36) NOT NULL,"
                + "victim_name VARCHAR(16) NOT NULL,"
                + "season INT NOT NULL,"
                + "kill_count INT NOT NULL DEFAULT 1,"
                + "first_kill BIGINT NOT NULL,"
                + "last_kill BIGINT NOT NULL,"
                + "PRIMARY KEY (killer_uuid, victim_uuid, season))";

        String metaTable = "CREATE TABLE IF NOT EXISTS meta ("
                + "meta_key VARCHAR(32) PRIMARY KEY,"
                + "meta_value VARCHAR(64) NOT NULL)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement s1 = conn.prepareStatement(killsTable);
             PreparedStatement s2 = conn.prepareStatement(metaTable)) {
            s1.executeUpdate();
            s2.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Tablolar olusturulamadi!", e);
        }
    }

    private void loadSeason() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT meta_value FROM meta WHERE meta_key = 'season'")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    currentSeason.set(Integer.parseInt(rs.getString("meta_value")));
                    return;
                }
            }
            // Ilk calistirma: sezonu 1 olarak kaydet.
            saveSeason(1);
        } catch (SQLException | NumberFormatException e) {
            plugin.getLogger().log(Level.SEVERE, "Sezon bilgisi yuklenemedi!", e);
        }
    }

    private void saveSeason(int season) {
        String sql = mysql
                ? "INSERT INTO meta (meta_key, meta_value) VALUES ('season', ?) "
                + "ON DUPLICATE KEY UPDATE meta_value = VALUES(meta_value)"
                : "INSERT INTO meta (meta_key, meta_value) VALUES ('season', ?) "
                + "ON CONFLICT(meta_key) DO UPDATE SET meta_value = excluded.meta_value";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, String.valueOf(season));
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Sezon bilgisi kaydedilemedi!", e);
        }
    }

    public int getCurrentSeason() {
        return currentSeason.get();
    }

    // ------------------------------------------------------------------
    //  Asenkron API
    // ------------------------------------------------------------------

    /**
     * Bir oldurmeyi kaydeder. Ayni kurban tekrar oldurulurse sayac artar.
     */
    public CompletableFuture<Void> recordKill(UUID killer, UUID victim, String victimName) {
        return CompletableFuture.runAsync(() -> {
            long now = System.currentTimeMillis();
            int season = currentSeason.get();
            String sql = mysql
                    ? "INSERT INTO kills (killer_uuid, victim_uuid, victim_name, season, kill_count, first_kill, last_kill) "
                    + "VALUES (?, ?, ?, ?, 1, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE kill_count = kill_count + 1, last_kill = VALUES(last_kill), victim_name = VALUES(victim_name)"
                    : "INSERT INTO kills (killer_uuid, victim_uuid, victim_name, season, kill_count, first_kill, last_kill) "
                    + "VALUES (?, ?, ?, ?, 1, ?, ?) "
                    + "ON CONFLICT(killer_uuid, victim_uuid, season) DO UPDATE SET "
                    + "kill_count = kill_count + 1, last_kill = excluded.last_kill, victim_name = excluded.victim_name";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, killer.toString());
                ps.setString(2, victim.toString());
                ps.setString(3, victimName);
                ps.setInt(4, season);
                ps.setLong(5, now);
                ps.setLong(6, now);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Oldurme kaydedilemedi!", e);
            }
        }, executor);
    }

    /**
     * Bir oyuncunun aktif sezondaki tum kurban kayitlarini (cok oldurulenden aza) getirir.
     */
    public CompletableFuture<List<KillEntry>> getKills(UUID killer) {
        return CompletableFuture.supplyAsync(() -> {
            List<KillEntry> entries = new ArrayList<>();
            String sql = "SELECT victim_uuid, victim_name, kill_count, first_kill, last_kill "
                    + "FROM kills WHERE killer_uuid = ? AND season = ? ORDER BY kill_count DESC, last_kill DESC";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, killer.toString());
                ps.setInt(2, currentSeason.get());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        entries.add(new KillEntry(
                                UUID.fromString(rs.getString("victim_uuid")),
                                rs.getString("victim_name"),
                                rs.getInt("kill_count"),
                                rs.getLong("first_kill"),
                                rs.getLong("last_kill")));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Kayitlar okunamadi!", e);
            }
            return entries;
        }, executor);
    }

    /**
     * Sezonu sifirlar (sezon numarasini bir artirir). Eski veriler silinmez,
     * sadece yeni sezonda gorunmez olur. Yeni sezon numarasini dondurur.
     */
    public CompletableFuture<Integer> resetSeason() {
        return CompletableFuture.supplyAsync(() -> {
            int next = currentSeason.incrementAndGet();
            saveSeason(next);
            return next;
        }, executor);
    }
}
