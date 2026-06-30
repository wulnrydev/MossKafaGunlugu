package dev.wulnrydev.mosskafagunlugu.config;

import dev.wulnrydev.mosskafagunlugu.MossKafaGunlugu;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * config.yml erisimini tek noktada toplayan sarmalayici.
 * Mesajlardaki %prefix% gibi yer tutucular burada cozumlenir.
 */
public final class ConfigManager {

    private final MossKafaGunlugu plugin;
    private FileConfiguration config;

    public ConfigManager(MossKafaGunlugu plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public FileConfiguration raw() {
        return config;
    }

    public String getPrefix() {
        return config.getString("messages.prefix", "");
    }

    /**
     * Mesaj anahtarini okur ve %prefix% yer tutucusunu cozer (renklendirme yapilmaz).
     */
    public String message(String key) {
        String value = config.getString("messages." + key, "");
        return value.replace("%prefix%", getPrefix());
    }

    public String guiTitle() {
        return config.getString("gui.title", "&0Kafa Günlüğü - Sayfa: %page%");
    }

    public int guiSize() {
        int size = config.getInt("settings.gui-size", 54);
        // 9'un kati ve gecerli aralikta olmasini garanti et. En az 18 olmalidir:
        // alt satir (9 yuva) navigasyona ayrildigindan icerik icin en az 9 yuva gerekir.
        if (size < 18 || size > 54 || size % 9 != 0) {
            return 54;
        }
        return size;
    }

    public String commandOnBack() {
        return config.getString("settings.command-on-back", "menü");
    }

    public String dateFormat() {
        return config.getString("date-format", "dd.MM.yyyy HH:mm");
    }

    public String headDisplayName() {
        return config.getString("gui.head-format.display-name", "&a%player_name%");
    }

    public List<String> headLore() {
        return config.getStringList("gui.head-format.lore");
    }

    public String buttonPath(String button, String key) {
        return config.getString("gui.buttons." + button + "." + key, "");
    }

    public List<String> buttonLore(String button) {
        return config.getStringList("gui.buttons." + button + ".lore");
    }

    public int buttonSlot(String button, int def) {
        return config.getInt("gui.buttons." + button + ".slot", def);
    }
}
