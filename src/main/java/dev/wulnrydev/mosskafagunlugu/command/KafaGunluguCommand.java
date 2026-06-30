package dev.wulnrydev.mosskafagunlugu.command;

import dev.wulnrydev.mosskafagunlugu.MossKafaGunlugu;
import dev.wulnrydev.mosskafagunlugu.gui.JournalGUI;
import dev.wulnrydev.mosskafagunlugu.util.ChatUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * /kafagunlugu komutunu ve alt komutlarini (reload, reset) isler.
 */
public final class KafaGunluguCommand implements TabExecutor {

    private final MossKafaGunlugu plugin;

    public KafaGunluguCommand(MossKafaGunlugu plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            openJournal(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "reset" -> handleReset(sender);
            default -> openJournal(sender);
        }
        return true;
    }

    private void openJournal(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            ChatUtil.send(sender, plugin.configManager().message("players-only"));
            return;
        }
        if (!player.hasPermission("mosskafagunlugu.use")) {
            ChatUtil.send(player, plugin.configManager().message("no-permission"));
            return;
        }

        // Kayitlar asenkron yuklenir, ardindan menu oyuncunun baglaminda acilir.
        plugin.databaseManager().getKills(player.getUniqueId()).thenAccept(entries -> {
            if (entries.isEmpty()) {
                ChatUtil.send(player, plugin.configManager().message("no-kills"));
                return;
            }
            new JournalGUI(plugin, player, entries).open(0);
        });
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("mosskafagunlugu.admin")) {
            ChatUtil.send(sender, plugin.configManager().message("no-permission"));
            return;
        }
        plugin.configManager().load();
        plugin.skullCache().clear();
        ChatUtil.send(sender, plugin.configManager().message("reload"));
    }

    private void handleReset(CommandSender sender) {
        if (!sender.hasPermission("mosskafagunlugu.admin")) {
            ChatUtil.send(sender, plugin.configManager().message("no-permission"));
            return;
        }
        plugin.databaseManager().resetSeason().thenAccept(newSeason ->
                ChatUtil.send(sender, plugin.configManager().message("reset")
                        .replace("%season%", String.valueOf(newSeason))));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("mosskafagunlugu.admin")) {
            List<String> options = new ArrayList<>();
            for (String sub : List.of("reload", "reset")) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    options.add(sub);
                }
            }
            return options;
        }
        return Collections.emptyList();
    }
}
