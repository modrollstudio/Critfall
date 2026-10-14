package studio.modroll.critfall.api.feedback;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.Entity;

/**
 * A name a flavor line puts in place of {@code {attacker}} or {@code {target}}. A mob's type name
 * travels as its translation key ({@code translatable}), so the client shows it in its own language;
 * a player's or a custom name travels as plain text. Flat strings only, like {@link ConsequenceLine}.
 */
public record FlavorName(String text, boolean translatable) {

    /** No name: the payload carries no flavor line. */
    public static final FlavorName NONE = new FlavorName("", false);

    public static FlavorName of(Entity entity) {
        Component name = entity.getName();
        if (name.getContents() instanceof TranslatableContents contents
                && contents.getArgs().length == 0
                && name.getSiblings().isEmpty()) {
            return new FlavorName(contents.getKey(), true);
        }
        return new FlavorName(name.getString(), false);
    }

    public static FlavorName literal(String text) {
        return new FlavorName(text, false);
    }

    public Component component() {
        return translatable ? Component.translatable(text) : Component.literal(text);
    }

    public static void write(FriendlyByteBuf buf, FlavorName name) {
        buf.writeUtf(name.text);
        buf.writeBoolean(name.translatable);
    }

    public static FlavorName read(FriendlyByteBuf buf) {
        return new FlavorName(buf.readUtf(), buf.readBoolean());
    }
}
