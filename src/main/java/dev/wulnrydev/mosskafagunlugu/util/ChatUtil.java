package dev.wulnrydev.mosskafagunlugu.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Hex (&#RRGGBB) ve standart (&a, &l ...) renk kodlarini Adventure
 * {@link Component} nesnelerine ceviren yardimci sinif.
 *
 * <p>Tum GUI metinleri ve sohbet mesajlari bu sinif uzerinden olusturulur,
 * boylece renk islemleri tek bir yerde toplanmis olur.</p>
 */
public final class ChatUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final char COLOR = ChatColor.COLOR_CHAR; // §

    // hexColors() etkin: §x§R§R§G§G§B§B (BungeeCord) formatini cozer.
    private static final LegacyComponentSerializer SERIALIZER =
            LegacyComponentSerializer.builder().hexColors().build();

    private ChatUtil() {
    }

    /**
     * Verilen ham metni (& ve &#RRGGBB kodlari ile) bir {@link Component} nesnesine cevirir.
     * GUI ogeleri icin varsayilan italik bezemesi kapatilir.
     */
    public static Component component(String input) {
        if (input == null) {
            return Component.empty();
        }
        Component component = SERIALIZER.deserialize(toSectionFormat(input));
        // Item isim/lore'larinda Minecraft varsayilani olan italik gorunumu kapat.
        return component.decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Bir liste icindeki tum satirlari renklendirir.
     */
    public static List<Component> components(List<String> lines) {
        return lines.stream().map(ChatUtil::component).collect(Collectors.toList());
    }

    /**
     * Renklendirilmis mesaji belirtilen alici(ya) gonderir.
     */
    public static void send(CommandSender sender, String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        sender.sendMessage(component(message));
    }

    /**
     * &#RRGGBB ve & kodlarini section (§) formatina cevirir.
     * Hex kodlari BungeeCord uyumlu §x§R§R§G§G§B§B bicimine donusturulur.
     */
    private static String toSectionFormat(String input) {
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuilder builder = new StringBuilder(input.length() + 16);
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder().append(COLOR).append('x');
            for (char c : hex.toCharArray()) {
                replacement.append(COLOR).append(c);
            }
            matcher.appendReplacement(builder, replacement.toString());
        }
        matcher.appendTail(builder);
        return ChatColor.translateAlternateColorCodes('&', builder.toString());
    }
}
