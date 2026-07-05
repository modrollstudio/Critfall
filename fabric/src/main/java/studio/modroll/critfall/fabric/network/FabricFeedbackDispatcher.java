package studio.modroll.critfall.fabric.network;

import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import studio.modroll.critfall.combat.CombatText;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.feedback.FeedbackSink;
import studio.modroll.critfall.feedback.RollFeedbackPayload;
import studio.modroll.critfall.feedback.SaveFeedbackPayload;

/**
 * Fabric twin of the NeoForge FeedbackDispatcher: sends the payload to clients that negotiated our
 * optional channel, and falls back to the {@link CombatText} action bar for vanilla/modless clients
 * so they still get a legible, consequence-carrying readout.
 */
public final class FabricFeedbackDispatcher {

    private FabricFeedbackDispatcher() {}

    public static FeedbackSink asSink() {
        return new FeedbackSink() {
            @Override
            public void roll(
                    LivingEntity attacker,
                    LivingEntity target,
                    RollFeedbackPayload payload,
                    Rules.FeedbackVisibility visibility) {
                dispatch(attacker, target, visibility, player -> sendRoll(player, payload));
            }

            @Override
            public void save(
                    LivingEntity attacker,
                    LivingEntity target,
                    SaveFeedbackPayload payload,
                    Rules.FeedbackVisibility visibility) {
                dispatch(attacker, target, visibility, player -> sendSave(player, payload));
            }
        };
    }

    private static void dispatch(
            LivingEntity attacker,
            LivingEntity target,
            Rules.FeedbackVisibility visibility,
            Consumer<ServerPlayer> send) {
        if (visibility == Rules.FeedbackVisibility.OFF) {
            return;
        }
        if (attacker instanceof ServerPlayer player) {
            send.accept(player);
        }
        if (visibility == Rules.FeedbackVisibility.EVERYONE && target instanceof ServerPlayer player) {
            send.accept(player);
        }
    }

    private static void sendRoll(ServerPlayer player, RollFeedbackPayload payload) {
        if (hasChannel(player, RollFeedbackPayload.TYPE)) {
            ServerPlayNetworking.send(player, payload);
        } else {
            player.displayClientMessage(CombatText.actionBar(payload), true);
        }
    }

    private static void sendSave(ServerPlayer player, SaveFeedbackPayload payload) {
        if (hasChannel(player, SaveFeedbackPayload.TYPE)) {
            ServerPlayNetworking.send(player, payload);
        } else {
            player.displayClientMessage(CombatText.actionBar(payload), true);
        }
    }

    /**
     * Whether this connection negotiated our optional channel — {@code false} for a modless/vanilla
     * client that never advertised support for this payload type.
     */
    private static boolean hasChannel(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return ServerPlayNetworking.canSend(player, type);
    }
}
