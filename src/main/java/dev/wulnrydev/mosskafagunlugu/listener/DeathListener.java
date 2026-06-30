package dev.wulnrydev.mosskafagunlugu.listener;

import dev.wulnrydev.mosskafagunlugu.MossKafaGunlugu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Oyuncu olumlerini dinler ve oldureni (varsa) asenkron olarak kaydeder.
 */
public final class DeathListener implements Listener {

    private final MossKafaGunlugu plugin;

    public DeathListener(MossKafaGunlugu plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        // Veri tabani islemi tamamen asenkron yurutulur.
        plugin.databaseManager().recordKill(killer.getUniqueId(), victim.getUniqueId(), victim.getName());
    }
}
