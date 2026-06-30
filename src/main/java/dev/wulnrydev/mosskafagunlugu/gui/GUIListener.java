package dev.wulnrydev.mosskafagunlugu.gui;

import dev.wulnrydev.mosskafagunlugu.MossKafaGunlugu;
import dev.wulnrydev.mosskafagunlugu.util.SchedulerUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Kafa gunlugu menusundeki tiklamalari yoneten dinleyici.
 */
public final class GUIListener implements Listener {

    private final MossKafaGunlugu plugin;

    public GUIListener(MossKafaGunlugu plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof JournalGUI gui)) {
            return;
        }
        // Menu icindeki tum etkilesimleri engelle (item alinamaz/tasinamaz).
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // Sadece ust (menu) envanterindeki tiklamalari isle.
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(gui.getInventory())) {
            return;
        }

        int slot = event.getRawSlot();

        if (slot == gui.backSlot()) {
            handleBack(player);
        } else if (slot == gui.nextSlot() && gui.hasNext()) {
            gui.switchPage(gui.getPage() + 1);
        } else if (slot == gui.prevSlot() && gui.hasPrevious()) {
            gui.switchPage(gui.getPage() - 1);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof JournalGUI) {
            event.setCancelled(true);
        }
    }

    private void handleBack(Player player) {
        String command = plugin.configManager().commandOnBack();
        player.closeInventory();
        if (command == null || command.isBlank()) {
            return;
        }
        // Komutu ana thread'de oyuncu baglaminda calistir.
        SchedulerUtil.runOnMain(plugin, () -> player.performCommand(command));
    }
}
