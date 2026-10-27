package studio.modroll.critfall.neoforge.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.gametest.JumpAttackScenarios;

/** NeoForge registration shim: delegates to the shared {@link JumpAttackScenarios} bodies. */
@GameTestHolder(Critfall.MOD_ID)
@PrefixGameTestTemplate(false)
public class JumpAttackGameTests {

    private static final String TEMPLATE = "empty";

    @GameTest(template = TEMPLATE)
    public void jumpHitRollsWithAdvantage(GameTestHelper helper) {
        JumpAttackScenarios.jumpHitRollsWithAdvantage(helper);
    }

    @GameTest(template = TEMPLATE)
    public void groundedHitRollsNormally(GameTestHelper helper) {
        JumpAttackScenarios.groundedHitRollsNormally(helper);
    }

    @GameTest(template = TEMPLATE)
    public void jumpHitWithDisadvantageSourceRollsNormally(GameTestHelper helper) {
        JumpAttackScenarios.jumpHitWithDisadvantageSourceRollsNormally(helper);
    }

    @GameTest(template = TEMPLATE)
    public void jumpHitDropsVanillaCritMultiplier(GameTestHelper helper) {
        JumpAttackScenarios.jumpHitDropsVanillaCritMultiplier(helper);
    }

    @GameTest(template = TEMPLATE)
    public void jumpAttackOffMatchesPreviousRelease(GameTestHelper helper) {
        JumpAttackScenarios.jumpAttackOffMatchesPreviousRelease(helper);
    }

    @GameTest(template = TEMPLATE)
    public void unrolledJumpHitKeepsVanillaCrit(GameTestHelper helper) {
        JumpAttackScenarios.unrolledJumpHitKeepsVanillaCrit(helper);
    }

    @GameTest(template = TEMPLATE)
    public void critsOffLeavesJumpCritToVanilla(GameTestHelper helper) {
        JumpAttackScenarios.critsOffLeavesJumpCritToVanilla(helper);
    }

    @GameTest(template = TEMPLATE)
    public void critsOffCarriesJumpCritOntoProfileDice(GameTestHelper helper) {
        JumpAttackScenarios.critsOffCarriesJumpCritOntoProfileDice(helper);
    }

    @GameTest(template = TEMPLATE)
    public void drivenAttacksIgnoreJumping(GameTestHelper helper) {
        JumpAttackScenarios.drivenAttacksIgnoreJumping(helper);
    }

    /** Stands in for another crit mod: makes the next swings crits at this multiplier; 0 is off. */
    private static volatile float otherModCrit;

    static {
        NeoForge.EVENT_BUS.addListener((CriticalHitEvent event) -> {
            if (otherModCrit > 0) {
                event.setCriticalHit(true);
                event.setDamageMultiplier(otherModCrit);
            }
        });
    }

    @GameTest(template = TEMPLATE)
    public void otherModsCritSurvivesCritsOff(GameTestHelper helper) {
        JumpAttackScenarios.otherModsCritSurvivesCritsOff(helper, multiplier -> otherModCrit = multiplier);
    }
}
