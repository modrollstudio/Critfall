package studio.modroll.critfall.fabric.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import studio.modroll.critfall.combat.AttackOutcome;
import studio.modroll.critfall.combat.CombatText;
import studio.modroll.critfall.feedback.ClientConfig;
import studio.modroll.critfall.feedback.RollFeedbackPayload;
import studio.modroll.critfall.feedback.SaveFeedbackPayload;

/**
 * Renders received feedback per {@link ClientConfig}: action-bar readout, flavor line in chat, sound,
 * and particles, each independently gated. Only ever referenced from {@link CritfallFabricClient}
 * (the client entrypoint), so the {@link Minecraft} references here never load on a dedicated server.
 * Behaviour is identical to the NeoForge client receiver.
 */
public final class ClientFeedbackReceiver {

    private ClientFeedbackReceiver() {}

    public static void renderRoll(RollFeedbackPayload payload) {
        ClientConfig config = CritfallFabricClient.config();
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

    public static void renderSave(SaveFeedbackPayload payload) {
        ClientConfig config = CritfallFabricClient.config();
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
