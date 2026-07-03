package studio.modroll.critfall.neoforge;

import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import studio.modroll.critfall.RollService;
import studio.modroll.critfall.combat.AttackResult;
import studio.modroll.critfall.combat.CombatEngine;
import studio.modroll.critfall.combat.CombatText;
import studio.modroll.critfall.combat.DamageCategory;
import studio.modroll.critfall.combat.DamageClassifier;
import studio.modroll.critfall.combat.Derivation;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.dice.DiceExpression;
import studio.modroll.critfall.dice.RollMode;

/**
 * Intercepts {@code LivingIncomingDamageEvent} — the earliest point in NeoForge's damage sequence
 * (after invulnerability checks, before any mitigation) — and replaces vanilla damage with a d20
 * attack roll vs derived AC. M2 scope: melee only; projectiles and spells pass through until M5.
 */
public final class DamageEventHandler {

    private DamageEventHandler() {}

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) {
            return;
        }
        Rules rules = RollService.rules();
        if (!rules.attackRolls()) {
            return;
        }
        DamageSource source = event.getSource();
        if (DamageClassifier.classify(source) != DamageCategory.MELEE) {
            return; // M2: only direct melee is rolled; everything else stays vanilla
        }
        LivingEntity attacker = (LivingEntity) source.getEntity(); // MELEE guarantees a living attacker
        boolean playerAttacker = attacker instanceof Player;
        if (playerAttacker ? !rules.attackRollsPlayers() : !rules.attackRollsMobs()) {
            return;
        }

        double attackAttribute = attacker.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
                ? attacker.getAttributeValue(Attributes.ATTACK_DAMAGE)
                : event.getAmount();
        int attackBonus = Derivation.attackBonus(attackAttribute);
        int armorClass = Derivation.armorClass(
                target.getAttributeValue(Attributes.ARMOR), target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        DiceExpression damageDice = Derivation.damageDice(event.getAmount());

        AttackResult result = CombatEngine.resolveAttack(
                RollService.roller(), rules, attackBonus, armorClass, RollMode.NORMAL, damageDice);

        switch (result.outcome()) {
            case MISS -> event.setCanceled(true);
            case FUMBLE -> {
                event.setCanceled(true);
                applyFumble(attacker, rules);
            }
            case HIT, CRIT -> {
                if (rules.damageDice()) {
                    event.setAmount(result.damage());
                }
                if (rules.disableVanillaArmorReduction()) {
                    // AC already represents armor — letting vanilla reduce again would double-dip.
                    event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> 0f);
                }
            }
        }
        if (rules.feedback()) {
            sendFeedback(attacker, target, result, damageDice);
        }
    }

    private static void applyFumble(LivingEntity attacker, Rules rules) {
        if (!rules.fumbleDurabilityBreak()) {
            return;
        }
        ItemStack weapon = attacker.getMainHandItem();
        if (weapon.isDamageableItem()) {
            int almostBroken = Math.max(0, weapon.getMaxDamage() - rules.fumbleSetDurabilityTo());
            weapon.setDamageValue(Math.max(weapon.getDamageValue(), almostBroken));
        }
    }

    private static void sendFeedback(
            LivingEntity attacker, LivingEntity target, AttackResult result, DiceExpression dice) {
        String text = CombatText.describe(result, dice.toString());
        if (attacker instanceof Player player) {
            player.displayClientMessage(Component.literal(text), true);
        }
        if (target instanceof Player player) {
            player.displayClientMessage(Component.literal(text), true);
        }
    }
}
