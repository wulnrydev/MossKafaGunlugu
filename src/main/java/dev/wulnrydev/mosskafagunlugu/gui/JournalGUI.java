package dev.wulnrydev.mosskafagunlugu.gui;

import dev.wulnrydev.mosskafagunlugu.MossKafaGunlugu;
import dev.wulnrydev.mosskafagunlugu.config.ConfigManager;
import dev.wulnrydev.mosskafagunlugu.data.KillEntry;
import dev.wulnrydev.mosskafagunlugu.util.ChatUtil;
import dev.wulnrydev.mosskafagunlugu.util.SchedulerUtil;
import dev.wulnrydev.mosskafagunlugu.util.SkullCache;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Sezonluk kafa gunlugu menusu. Hem {@link InventoryHolder} olarak menuyu
 * tanimlar hem de sayfalama (pagination) ve kafa olusturma mantigini icerir.
 */
public final class JournalGUI implements InventoryHolder {

    private final MossKafaGunlugu plugin;
    private final ConfigManager config;
    private final SkullCache skullCache;
    private final Player viewer;
    private final List<KillEntry> entries;

    private final int size;
    private final int perPage;
    private final int totalPages;
    private final DateTimeFormatter dateFormatter;

    private int page;
    private Inventory inventory;

    public JournalGUI(MossKafaGunlugu plugin, Player viewer, List<KillEntry> entries) {
        this.plugin = plugin;
        this.config = plugin.configManager();
        this.skullCache = plugin.skullCache();
        this.viewer = viewer;
        this.entries = entries;
        this.size = config.guiSize();
        // Son satir navigasyon icin ayrilir. guiSize() en az 18 dondurdugunden
        // perPage >= 9'dur; yine de sifira bolme/negatif degerlere karsi korunur.
        this.perPage = Math.max(1, size - 9);
        this.totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) perPage));
        this.dateFormatter = DateTimeFormatter.ofPattern(config.dateFormat()).withZone(ZoneId.systemDefault());
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public int getPage() {
        return page;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public boolean hasNext() {
        return page < totalPages - 1;
    }

    public boolean hasPrevious() {
        return page > 0;
    }

    public int backSlot() {
        return config.buttonSlot("back", 45);
    }

    public int nextSlot() {
        return config.buttonSlot("next-page", 53);
    }

    public int prevSlot() {
        return config.buttonSlot("previous-page", 46);
    }

    /**
     * Menuyu olusturup oyuncuya gosterir. Ana thread'de calistirilir.
     */
    public void open(int page) {
        this.page = Math.max(0, Math.min(page, totalPages - 1));
        SchedulerUtil.runOnMain(plugin, () -> {
            build();
            viewer.openInventory(inventory);
        });
    }

    /**
     * Sayfa degistirir ve menuyu yeniden cizer (yeni inventory acmadan).
     */
    public void switchPage(int newPage) {
        this.page = Math.max(0, Math.min(newPage, totalPages - 1));
        build();
        viewer.openInventory(inventory);
    }

    // ------------------------------------------------------------------
    //  Cizim
    // ------------------------------------------------------------------

    private void build() {
        String title = config.guiTitle().replace("%page%", String.valueOf(page + 1));
        this.inventory = Bukkit.createInventory(this, size, ChatUtil.component(title));

        // Alt satiri doldurucu cam ile doldur.
        ItemStack filler = buildFiller();
        for (int slot = perPage; slot < size; slot++) {
            inventory.setItem(slot, filler);
        }

        // Geri butonu.
        inventory.setItem(backSlot(), buildButton("back", Material.ARROW));

        // Sayfa butonlari yalnizca ilgili sayfa varsa gosterilir, yoksa cam kalir.
        if (hasPrevious()) {
            inventory.setItem(prevSlot(), buildButton("previous-page", Material.PLAYER_HEAD));
        }
        if (hasNext()) {
            inventory.setItem(nextSlot(), buildButton("next-page", Material.PLAYER_HEAD));
        }

        // Bu sayfadaki kafalar.
        int start = page * perPage;
        for (int i = 0; i < perPage; i++) {
            int index = start + i;
            if (index >= entries.size()) {
                break;
            }
            KillEntry entry = entries.get(index);
            inventory.setItem(i, buildHead(entry, i));
        }
    }

    private ItemStack buildFiller() {
        Material material = material(config.buttonPath("filler", "material"), Material.GRAY_STAINED_GLASS_PANE);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ChatUtil.component(config.buttonPath("filler", "display-name")));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildButton(String key, Material fallback) {
        Material material = material(config.buttonPath(key, "material"), fallback);
        ItemStack item = new ItemStack(material);

        if (material == Material.PLAYER_HEAD) {
            String texture = config.buttonPath(key, "texture");
            if (texture != null && !texture.isEmpty()) {
                ItemMeta base = item.getItemMeta();
                if (base instanceof SkullMeta skull) {
                    skull.setOwnerProfile(skullCache.profileFromBase64(texture));
                    item.setItemMeta(skull);
                }
            }
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ChatUtil.component(config.buttonPath(key, "display-name")));
            List<String> lore = config.buttonLore(key);
            if (!lore.isEmpty()) {
                meta.lore(ChatUtil.components(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Tek bir kurban kafasi olusturur. Profil onbellekte yoksa once isimsiz
     * kafa gosterilir, ardindan asenkron cozulup ilgili yuva guncellenir.
     */
    private ItemStack buildHead(KillEntry entry, int slot) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            applyHeadDisplay(meta, entry);
            PlayerProfile cached = skullCache.getCached(entry.victimUuid());
            if (cached != null) {
                meta.setOwnerProfile(cached);
            }
            item.setItemMeta(meta);
        }

        // Onbellekte yoksa asenkron coz ve menu hala aciksa yuvayi guncelle.
        if (skullCache.getCached(entry.victimUuid()) == null) {
            int builtPage = page;
            skullCache.resolve(entry.victimUuid()).thenAccept(profile ->
                    SchedulerUtil.runOnMain(plugin, () -> refreshHead(slot, builtPage, entry, profile)));
        }
        return item;
    }

    private void refreshHead(int slot, int builtPage, KillEntry entry, PlayerProfile profile) {
        // Menu hala acik ve ayni sayfada mi?
        if (inventory == null || page != builtPage) {
            return;
        }
        if (!(viewer.getOpenInventory().getTopInventory().getHolder() instanceof JournalGUI gui) || gui != this) {
            return;
        }
        ItemStack current = inventory.getItem(slot);
        if (current == null || current.getType() != Material.PLAYER_HEAD) {
            return;
        }
        SkullMeta meta = (SkullMeta) current.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.setOwnerProfile(profile);
        applyHeadDisplay(meta, entry);
        current.setItemMeta(meta);
        inventory.setItem(slot, current);
    }

    private void applyHeadDisplay(SkullMeta meta, KillEntry entry) {
        meta.displayName(ChatUtil.component(
                config.headDisplayName().replace("%player_name%", entry.victimName())));

        List<String> raw = config.headLore();
        List<String> resolved = new ArrayList<>(raw.size());
        for (String line : raw) {
            resolved.add(line
                    .replace("%player_name%", entry.victimName())
                    .replace("%kill_count%", String.valueOf(entry.killCount()))
                    .replace("%first_kill_date%", formatDate(entry.firstKill()))
                    .replace("%last_kill_date%", formatDate(entry.lastKill())));
        }
        meta.lore(ChatUtil.components(resolved));
    }

    private String formatDate(long epochMillis) {
        return dateFormatter.format(Instant.ofEpochMilli(epochMillis));
    }

    private Material material(String name, Material fallback) {
        if (name == null || name.isEmpty()) {
            return fallback;
        }
        Material material = Material.matchMaterial(name.toUpperCase());
        return material != null ? material : fallback;
    }
}
