package dev.wulnrydev.mosskafagunlugu.data;

import java.util.UUID;

/**
 * Tek bir kurban kaydini temsil eden hafif veri yapisi (immutable record).
 *
 * @param victimUuid kurbanin UUID'si
 * @param victimName kurbanin son bilinen ismi (kafa/lore icin)
 * @param killCount  bu sezonda kac kez olduruldugu
 * @param firstKill  ilk oldurme zamani (epoch millis)
 * @param lastKill   son oldurme zamani (epoch millis)
 */
public record KillEntry(UUID victimUuid, String victimName, int killCount, long firstKill, long lastKill) {
}
