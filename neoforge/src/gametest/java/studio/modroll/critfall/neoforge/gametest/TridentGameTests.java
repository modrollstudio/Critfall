package studio.modroll.critfall.neoforge.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.gametest.TridentScenarios;

/** NeoForge registration shim: delegates to the shared {@link TridentScenarios} bodies (issue #3). */
@GameTestHolder(Critfall.MOD_ID)
@PrefixGameTestTemplate(false)
public class TridentGameTests {

    private static final String TEMPLATE = "empty";

    @GameTest(template = TEMPLATE)
    public void meleeTridentStabUsesMeleeProfile(GameTestHelper helper) {
        TridentScenarios.meleeTridentStabUsesMeleeProfile(helper);
    }

    @GameTest(template = TEMPLATE)
    public void thrownTridentUsesThrownProfile(GameTestHelper helper) {
        TridentScenarios.thrownTridentUsesThrownProfile(helper);
    }

    @GameTest(template = TEMPLATE)
    public void meleeTridentCritPicksMeleeFlavorPool(GameTestHelper helper) {
        TridentScenarios.meleeTridentCritPicksMeleeFlavorPool(helper);
    }

    @GameTest(template = TEMPLATE)
    public void thrownTridentCritPicksThrownFlavorPool(GameTestHelper helper) {
        TridentScenarios.thrownTridentCritPicksThrownFlavorPool(helper);
    }
}
