package studio.modroll.critfall.neoforge.network;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import studio.modroll.critfall.combat.AttackOutcome;
import studio.modroll.critfall.combat.CombatText;
import studio.modroll.critfall.feedback.ClientConfig;
import studio.modroll.critfall.feedback.RollFeedbackPayload;
import studio.modroll.critfall.feedback.SaveFeedbackPayload;
import studio.modroll.critfall.neoforge.client.CritfallClient;

/**
 * Renders received feedback per {@link ClientConfig}: action-bar readout, flavor line in chat, sound,
 * and particles, each independently gated. Only ever registered as a payload handler on the physical
 * client ({@code CritfallPayloads}), so the {@link Minecraft} references here never load on a
 * dedicated server.
 */
public final class ClientFeedbackReceiver {

    private ClientFeedbackReceiver() {}

    public static void receiveRoll(RollFeedbackPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> renderRoll(payload));
    }

    public static void receiveSave(SaveFeedbackPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> renderSave(payload));
    }

    private static void renderRoll(RollFeedbackPayload payload) {
        ClientConfig config = CritfallClient.config();
        Minecraft mc = Minecraft.getInstance();
        if (config.rolls()) {
            mc.gui.setOverlayMessage(CombatText.actionBar(payload), false);
        }
        if (config.flavor()) {
            payload.flavorKey().ifPresent(key -> mc.gui.getChat().addMessage(Component.translatable(key)));
        }
        if (config.sounds()) {
            playSound(payload.outcome());
        }
        if (config.particles()) {
            spawnParticles(payload.outcome());
        }
    }

    private static void renderSave(SaveFeedbackPayload payload) {
        ClientConfig config = CritfallClient.config();
        Minecraft mc = Minecraft.getInstance();
        if (config.rolls()) {
            mc.gui.setOverlayMessage(CombatText.actionBar(payload), false);
        }
        if (config.flavor()) {
            payload.flavorKey().ifPresent(key -> mc.gui.getChat().addMessage(Component.translatable(key)));
        }
    }

    private static void playSound(AttackOutcome outcome) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        SoundEvent sound =
                switch (outcome) {
                    case CRIT -> SoundEvents.PLAYER_LEVELUP;
                    case FUMBLE -> SoundEvents.ITEM_BREAK;
                    case HIT, MISS -> SoundEvents.UI_BUTTON_CLICK.value();
                };
        mc.player.playSound(sound, 0.5F, outcome == AttackOutcome.CRIT ? 1.2F : 1.0F);
    }

    private static void spawnParticles(AttackOutcome outcome) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        ParticleOptions particle =
                switch (outcome) {
                    case CRIT -> ParticleTypes.CRIT;
                    case FUMBLE -> ParticleTypes.SMOKE;
                    default -> null;
                };
        if (particle != null) {
            var p = mc.player;
            mc.level.addParticle(particle, p.getX(), p.getEyeY(), p.getZ(), 0, 0, 0);
        }
    }
}
