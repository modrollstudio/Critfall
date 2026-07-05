package studio.modroll.critfall.neoforge.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.combat.CombatText;
import studio.modroll.critfall.feedback.ConsequenceLine;
import studio.modroll.critfall.neoforge.network.FeedbackDispatcher;

/**
 * The modless fallback must stay legible: a vanilla-client player still learns WHICH consequence
 * fired, not just the bare roll. Driven through the real damage handler (Task 12) with scripted RNG.
 */
@GameTestHolder(Critfall.MOD_ID)
@PrefixGameTestTemplate(false)
public class FeedbackGameTests {

    private static final String TEMPLATE = "empty";

    @GameTest(template = TEMPLATE)
    public void modlessFallbackCarriesConsequenceText(GameTestHelper helper) {
        FeedbackDispatcher.resetForTest();
        Husk husk = CombatGameTests.spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = CombatGameTests.spawnCalm(helper, EntityType.PIG, 3, 3);
        husk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        CombatGameTests.withRolls(
                helper,
                studio.modroll.critfall.combat.Rules.DEFAULTS,
                () -> {
                    // nat 1, confirmation 2 < DC 10 fails -> fumble confirmed, table pick 1 = durability
                    pig.hurt(helper.getLevel().damageSources().mobAttack(husk), 3.0F);
                    var payload = FeedbackDispatcher.lastRollPayload;
                    if (payload == null
                            || payload.consequences().stream()
                                    .noneMatch(c -> c.key().equals(ConsequenceLine.DURABILITY_BROKEN))) {
                        helper.fail("dispatched payload should carry the durability consequence, was " + payload);
                    }
                    String fallback = CombatText.actionBar(payload).getString();
                    if (!fallback.contains("durability.broken")
                            && !fallback.toLowerCase().contains("weapon")) {
                        helper.fail("modless fallback must announce the consequence, was: " + fallback);
                    }
                },
                1,
                2,
                1);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void critDispatchesCritPayload(GameTestHelper helper) {
        FeedbackDispatcher.resetForTest();
        Husk husk = CombatGameTests.spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = CombatGameTests.spawnCalm(helper, EntityType.PIG, 3, 3);
        CombatGameTests.withRolls(
                helper,
                studio.modroll.critfall.combat.Rules.DEFAULTS,
                () -> {
                    pig.hurt(helper.getLevel().damageSources().mobAttack(husk), 3.0F); // nat 20 -> crit
                    var payload = FeedbackDispatcher.lastRollPayload;
                    if (payload == null || payload.outcome() != studio.modroll.critfall.combat.AttackOutcome.CRIT) {
                        helper.fail("nat 20 should dispatch a CRIT payload, was " + payload);
                    }
                },
                20);
        helper.succeed();
    }
}
