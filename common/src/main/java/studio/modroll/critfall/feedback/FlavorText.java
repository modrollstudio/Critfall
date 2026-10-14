package studio.modroll.critfall.feedback;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import studio.modroll.critfall.api.feedback.FlavorName;

/**
 * Turns a flavor key into the line the client shows: the translated text with {@code {attacker}} and
 * {@code {target}} replaced by the names the payload carried. Lang files keep the braces as written,
 * since Minecraft's own {@code %s} formatting does not apply to these lines.
 */
public final class FlavorText {

    public static final String ATTACKER = "{attacker}";
    public static final String TARGET = "{target}";

    private FlavorText() {}

    /** The line for {@code key} in the current language. */
    public static Component line(String key, FlavorName attacker, FlavorName target) {
        return fill(Language.getInstance().getOrDefault(key), attacker.component(), target.component());
    }

    /** {@code pattern} with each placeholder replaced by its name. */
    public static MutableComponent fill(String pattern, Component attacker, Component target) {
        MutableComponent line = Component.empty();
        int from = 0;
        while (from < pattern.length()) {
            int atAttacker = pattern.indexOf(ATTACKER, from);
            int atTarget = pattern.indexOf(TARGET, from);
            int next = atAttacker < 0 ? atTarget : atTarget < 0 ? atAttacker : Math.min(atAttacker, atTarget);
            if (next < 0) {
                line.append(pattern.substring(from));
                break;
            }
            if (next > from) {
                line.append(pattern.substring(from, next));
            }
            boolean isAttacker = next == atAttacker;
            line.append(isAttacker ? attacker : target);
            from = next + (isAttacker ? ATTACKER : TARGET).length();
        }
        return line;
    }
}
