package studio.modroll.critfall.neoforge.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.gametest.RulesConfigScenarios;

/** NeoForge registration shim: delegates to the shared {@link RulesConfigScenarios} bodies. */
@GameTestHolder(Critfall.MOD_ID)
@PrefixGameTestTemplate(false)
public class RulesConfigGameTests {

    private static final String TEMPLATE = "empty";

    @GameTest(template = TEMPLATE)
    public void savedRulesReachTheLiveGame(GameTestHelper helper) {
        RulesConfigScenarios.savedRulesReachTheLiveGame(helper);
    }

    @GameTest(template = TEMPLATE)
    public void invalidInputIsRejectedLikeTheLoader(GameTestHelper helper) {
        RulesConfigScenarios.invalidInputIsRejectedLikeTheLoader(helper);
    }

    @GameTest(template = TEMPLATE)
    public void weaponDamageIsOffOnlyInFreshConfigs(GameTestHelper helper) {
        RulesConfigScenarios.weaponDamageIsOffOnlyInFreshConfigs(helper);
    }
}
