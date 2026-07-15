package studio.modroll.critfall.neoforge.network;

import java.util.function.BiConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import studio.modroll.critfall.api.feedback.RollFeedbackPayload;
import studio.modroll.critfall.combat.CombatText;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.feedback.SaveFeedbackPayload;

/**
 * Sends feedback to the players a roll's {@code roll_visibility} allows: the packet to mod clients,
 * the plain {@link CombatText} action bar to connections without our optional channel (so vanilla
 * clients still get a legible, consequence-carrying readout). The sink is swappable for GameTests.
 */
public final class FeedbackDispatcher {

    /**
     * Test seam: how the fallback text reaches a player. Real impl writes to the action bar.
     * Public (rather than package-private) so GameTests in the sibling {@code gametest} package can
     * swap it; this is a test seam, not part of the mod's public API.
     */
    public static volatile BiConsumer<ServerPlayer, Component> actionBarSink =
            (player, text) -> player.displayClientMessage(text, true);

    /** Test seam: the last roll payload dispatched, for GameTest assertions. */
    public static volatile RollFeedbackPayload lastRollPayload;

    private FeedbackDispatcher() {}

    public static void dispatchRoll(
            LivingEntity attacker,
            LivingEntity target,
            RollFeedbackPayload payload,
            Rules.FeedbackVisibility visibility) {
        lastRollPayload = payload;
        if (visibility == Rules.FeedbackVisibility.OFF) {
            return;
        }
        if (attacker instanceof ServerPlayer player) {
            sendRoll(player, payload);
        }
        if (visibility == Rules.FeedbackVisibility.EVERYONE && target instanceof ServerPlayer player) {
            sendRoll(player, payload);
        }
    }

    public static void dispatchSave(
            LivingEntity attacker,
            LivingEntity target,
            SaveFeedbackPayload payload,
            Rules.FeedbackVisibility visibility) {
        if (visibility == Rules.FeedbackVisibility.OFF) {
            return;
        }
        if (attacker instanceof ServerPlayer player) {
            sendSave(player, payload);
        }
        if (visibility == Rules.FeedbackVisibility.EVERYONE && target instanceof ServerPlayer player) {
            sendSave(player, payload);
        }
    }

    private static void sendRoll(ServerPlayer player, RollFeedbackPayload payload) {
        if (hasChannel(player, RollFeedbackPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, payload);
        } else {
            actionBarSink.accept(player, CombatText.actionBar(payload));
        }
    }

    private static void sendSave(ServerPlayer player, SaveFeedbackPayload payload) {
        if (hasChannel(player, SaveFeedbackPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, payload);
        } else {
            actionBarSink.accept(player, CombatText.actionBar(payload));
        }
    }

    /**
     * Whether this connection negotiated our optional channel — {@code false} for a modless/vanilla
     * client that never advertised support for this payload type.
     */
    private static boolean hasChannel(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return player.connection.hasChannel(type);
    }

    public static void resetForTest() {
        lastRollPayload = null;
        actionBarSink = (player, text) -> player.displayClientMessage(text, true);
    }

    /** Adapts the dispatcher to the common {@link studio.modroll.critfall.feedback.FeedbackSink} seam. */
    public static studio.modroll.critfall.feedback.FeedbackSink asSink() {
        return new studio.modroll.critfall.feedback.FeedbackSink() {
            @Override
            public void roll(
                    LivingEntity attacker,
                    LivingEntity target,
                    RollFeedbackPayload payload,
                    Rules.FeedbackVisibility visibility) {
                dispatchRoll(attacker, target, payload, visibility);
            }

            @Override
            public void save(
                    LivingEntity attacker,
                    LivingEntity target,
                    SaveFeedbackPayload payload,
                    Rules.FeedbackVisibility visibility) {
                dispatchSave(attacker, target, payload, visibility);
            }
        };
    }
}
