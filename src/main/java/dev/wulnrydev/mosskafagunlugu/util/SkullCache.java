package dev.wulnrydev.mosskafagunlugu.util;

import org.bukkit.Bukkit;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Oyuncu kafalarinin (skin/profil) onbellege alindigi yardimci sinif.
 *
 * <p><b>Performans:</b> Profiller hicbir zaman ana thread'de Mojang API'sinden
 * cekilmez. {@link PlayerProfile#update()} asenkron calisir ve sonuc lokal
 * olarak onbellege alinir. Boylece GUI acilirken TPS dususu yasanmaz.</p>
 */
public final class SkullCache {

    private static final Pattern URL_PATTERN = Pattern.compile("\"url\"\\s*:\\s*\"(http[^\"]+)\"");

    /** Profil onbellegi icin ust sinir; bellek kullanimini sinirlar. */
    private static final int MAX_CACHE_SIZE = 1000;

    private final Logger logger;

    /** UUID -> tamamlanmis (texturlu) profil onbellegi. */
    private final ConcurrentHashMap<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();

    /** Ayni UUID icin tekrarlayan eszamanli istekleri tekillestirir. */
    private final ConcurrentHashMap<UUID, CompletableFuture<PlayerProfile>> pending = new ConcurrentHashMap<>();

    /** Base64 doku -> cozulmus statik profil (sayfa oklari gibi sabit kafalar). */
    private final ConcurrentHashMap<String, PlayerProfile> textureCache = new ConcurrentHashMap<>();

    public SkullCache(Logger logger) {
        this.logger = logger;
    }

    /**
     * Onbellekte hazir profil varsa dondurur, yoksa {@code null} doner.
     * (Ana thread'de aninda kullanim icindir.)
     */
    public PlayerProfile getCached(UUID uuid) {
        return cache.get(uuid);
    }

    /**
     * Belirtilen oyuncunun profilini asenkron olarak cozer ve onbellege alir.
     * Ayni UUID icin paralel istekler tek bir gorevde birlestirilir.
     */
    public CompletableFuture<PlayerProfile> resolve(UUID uuid) {
        PlayerProfile cached = cache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return pending.computeIfAbsent(uuid, id -> {
            PlayerProfile profile = Bukkit.createPlayerProfile(id);
            // update() Paper'da asenkron calisir ve CompletableFuture dondurur.
            return profile.update().handle((updated, throwable) -> {
                pending.remove(id);
                if (throwable != null) {
                    logger.log(Level.FINE, "Profil cozumlenemedi: " + id, throwable);
                    return profile; // En azindan UUID tabanli profili dondur.
                }
                evictIfFull();
                cache.put(id, updated);
                return updated;
            });
        });
    }

    /**
     * Onbellek dolduysa rastgele bir girdiyi cikartarak yumusak ust sinir uygular.
     */
    private void evictIfFull() {
        if (cache.size() >= MAX_CACHE_SIZE) {
            Iterator<UUID> it = cache.keySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
    }

    /**
     * Base64 kodlu bir doku (texture) degerinden statik profil olusturur.
     * Sonuc onbellege alinir; ayni doku icin tekrar cozumleme yapilmaz
     * (Mojang istegi de yapilmaz).
     */
    public PlayerProfile profileFromBase64(String base64) {
        return textureCache.computeIfAbsent(base64, this::decodeBase64Profile);
    }

    private PlayerProfile decodeBase64Profile(String base64) {
        PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
        try {
            String json = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
            Matcher matcher = URL_PATTERN.matcher(json);
            if (matcher.find()) {
                PlayerTextures textures = profile.getTextures();
                textures.setSkin(new URL(matcher.group(1)));
                profile.setTextures(textures);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Gecersiz kafa doku (texture) degeri.", e);
        }
        return profile;
    }

    public void clear() {
        cache.clear();
        pending.clear();
        textureCache.clear();
    }
}
