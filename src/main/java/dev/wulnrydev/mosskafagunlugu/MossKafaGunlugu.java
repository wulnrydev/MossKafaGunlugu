package dev.wulnrydev.mosskafagunlugu;

import dev.wulnrydev.mosskafagunlugu.command.KafaGunluguCommand;
import dev.wulnrydev.mosskafagunlugu.config.ConfigManager;
import dev.wulnrydev.mosskafagunlugu.data.DatabaseManager;
import dev.wulnrydev.mosskafagunlugu.gui.GUIListener;
import dev.wulnrydev.mosskafagunlugu.listener.DeathListener;
import dev.wulnrydev.mosskafagunlugu.util.SkullCache;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MossKafaGunlugu - 1.20+ icin yuksek optimizasyonlu sezonluk kafa gunlugu eklentisi.
 *
 * @author wulnrydev
 */
public final class MossKafaGunlugu extends JavaPlugin {

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private SkullCache skullCache;

    @Override
    public void onEnable() {
        // Yapilandirma
        this.configManager = new ConfigManager(this);
        this.configManager.load();

        // Onbellek
        this.skullCache = new SkullCache(getLogger());

        // Veri tabani (asenkron havuz baslatilir)
        this.databaseManager = new DatabaseManager(this);
        this.databaseManager.init();

        // Komut
        PluginCommand command = getCommand("kafagunlugu");
        if (command != null) {
            command.setExecutor(new KafaGunluguCommand(this));
        } else {
            getLogger().severe("'kafagunlugu' komutu plugin.yml icinde bulunamadi!");
        }

        // Dinleyiciler
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(new DeathListener(this), this);

        getLogger().info("MossKafaGunlugu etkinlestirildi.");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        if (skullCache != null) {
            skullCache.clear();
        }
        getLogger().info("MossKafaGunlugu devre disi birakildi.");
    }

    public ConfigManager configManager() {
        return configManager;
    }

    public DatabaseManager databaseManager() {
        return databaseManager;
    }

    public SkullCache skullCache() {
        return skullCache;
    }
}
