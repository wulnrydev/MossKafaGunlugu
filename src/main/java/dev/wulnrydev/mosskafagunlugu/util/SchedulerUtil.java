package dev.wulnrydev.mosskafagunlugu.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Ana thread (Main Thread) zamanlayici soyutlamasi.
 *
 * <p>Asenkron tamamlanan gorevlerin (veri tabani / kafa cozumleme) sonuclari
 * Bukkit API'sine dokunmadan once mutlaka ana thread'e geri tasinmalidir.
 * Bu yardimci, cagrildigi yer zaten ana thread ise gorevi aninda calistirir,
 * degilse bir sonraki tick'te ana thread'de calistirir.</p>
 */
public final class SchedulerUtil {

    private SchedulerUtil() {
    }

    /**
     * Verilen gorevi ana thread'de calistirir. GUI acma/guncelleme ve komut
     * calistirma gibi Bukkit API'si gerektiren islemler icin kullanilir.
     *
     * <p>Eklenti devre disi birakilmissa (sunucu kapanisi/reload) gec gelen
     * asenkron sonuclar sessizce yok sayilir; boylece kapanis sirasinda
     * {@code IllegalPluginAccessException} olusmaz.</p>
     */
    public static void runOnMain(Plugin plugin, Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
}
