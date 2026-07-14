package studio.modroll.critfall.gametest;

import java.util.Random;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import studio.modroll.critfall.RollRuntime;
import studio.modroll.critfall.api.dice.DiceRoller;
import studio.modroll.critfall.combat.FumbleCooldowns;
import studio.modroll.critfall.combat.Rules;

/**
 * Dry-run mode (PLAN §8.2.3): rolls are computed and shown, but vanilla damage still applies and no
 * outcome effect fires. Forcing the very scenario that normally sets a weapon to 1 durability
 * ({@link CombatScenarios#fumbleConfirmationFailureTriggersConsequence}, faces {@code 1,5,1}) proves
 * suppression two ways: the weapon stays whole, the vanilla hit lands, and only {@code 1,5} are
 * consumed — the third die (the outcome-table pick) is never rolled because the executor is skipped.
 */
public final class DryRunScenarios {

    private static final float VANILLA_HIT = 3.0F;

    private DryRunScenarios() {}

    public static void dryRunLeavesWorldAndWeaponUntouched(GameTestHelper helper) {
        Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        husk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));

        Rules base = Rules.DEFAULTS;
        Rules dryRun = new Rules(
                base.attackRolls(),
                base.damageDice(),
                base.crits(),
                base.fumbles(),
                base.spells(),
                base.fallbacks(),
                base.feedback(),
                base.balance(),
                new Rules.DryRun(true));

        // Mirrors withRolls (package-private), but this scenario needs the assertions AFTER the
        // roller/rules restore, and the shared helper self-contains that; reuse it directly.
        ScriptedRandom scripted = ScriptedRandom.ofDieFaces(1, 5); // nat 1, confirmation 5 < 10 -> fumble confirmed
        Rules before = RollRuntime.rules();
        RollRuntime.setRoller(new DiceRoller(scripted));
        RollRuntime.setRules(dryRun);
        FumbleCooldowns.clear();
        try {
            pig.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);

            // Vanilla damage passed through untouched (not cancelled, not replaced by dice).
            if (Math.abs(pig.getHealth() - (pig.getMaxHealth() - VANILLA_HIT)) > 0.001F) {
                helper.fail("dry-run must let the vanilla " + VANILLA_HIT + " through; health was " + pig.getHealth());
            }
            // The fumble effect never fired: the weapon is whole.
            if (husk.getMainHandItem().getDamageValue() != 0) {
                helper.fail("dry-run must not damage the weapon (outcome effect suppressed)");
            }
            // Only the to-hit and confirmation dice were consumed; the table pick was never rolled.
            if (!scripted.isExhausted()) {
                helper.fail("dry-run should skip the outcome-table pick die");
            }
        } finally {
            RollRuntime.setRoller(new DiceRoller(new Random()));
            RollRuntime.setRules(before);
            FumbleCooldowns.clear();
        }
        helper.succeed();
    }
}
