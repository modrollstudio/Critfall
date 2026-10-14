package studio.modroll.critfall.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageTypes;
import org.junit.jupiter.api.Test;
import studio.modroll.critfall.api.combat.SuccessDamage;
import studio.modroll.critfall.api.dice.DiceExpression;
import studio.modroll.critfall.api.dice.RollMode;

class HazardSpecTest {

    private static final DiceExpression DICE = DiceExpression.parse("2d6");

    @Test
    void saveDefaultsToHalfOnSuccessAndANormalRoll() {
        HazardSave save = HazardSave.of("Gas vent", DamageTypes.MAGIC, ModifierProvider.CONSTITUTION_SAVE, 12, DICE);
        assertEquals(SuccessDamage.HALF, save.onSuccess());
        assertEquals(RollMode.NORMAL, save.mode());
        assertEquals("critfall:constitution", save.saveKey());
    }

    @Test
    void saveWithersCopy() {
        HazardSave save = HazardSave.of("Pit", DamageTypes.FALL, ModifierProvider.DEXTERITY_SAVE, 13, DICE)
                .withOnSuccess(SuccessDamage.NONE)
                .withMode(RollMode.DISADVANTAGE);
        assertEquals(SuccessDamage.NONE, save.onSuccess());
        assertEquals(RollMode.DISADVANTAGE, save.mode());
        assertEquals(13, save.dc());
    }

    @Test
    void attackDefaultsToANormalRoll() {
        HazardAttack attack = HazardAttack.of("Darts", DamageTypes.ARROW, 5, DICE);
        assertEquals(RollMode.NORMAL, attack.mode());
        assertEquals(RollMode.ADVANTAGE, attack.withMode(RollMode.ADVANTAGE).mode());
        assertEquals(5, attack.withMode(RollMode.ADVANTAGE).attackBonus());
    }

    @Test
    void requiredPartsAreChecked() {
        assertThrows(NullPointerException.class, () -> HazardSave.of(null, DamageTypes.FALL, "k", 10, DICE));
        assertThrows(NullPointerException.class, () -> HazardSave.of("Pit", null, "k", 10, DICE));
        assertThrows(NullPointerException.class, () -> HazardAttack.of("Darts", DamageTypes.ARROW, 5, null));
    }

    @Test
    void idIsEmptyUnlessGiven() {
        ResourceLocation id = ResourceLocation.parse("crawl:trap/spikes");
        HazardSave save = HazardSave.of("Pit", DamageTypes.FALL, ModifierProvider.DEXTERITY_SAVE, 13, DICE);
        assertEquals(Optional.empty(), save.id());
        HazardSave withId = save.withId(id).withMode(RollMode.ADVANTAGE).withOnSuccess(SuccessDamage.NONE);
        assertEquals(Optional.of(id), withId.id());
        HazardAttack attack = HazardAttack.of("Darts", DamageTypes.ARROW, 5, DICE);
        assertEquals(Optional.empty(), attack.id());
        assertEquals(
                Optional.of(id), attack.withId(id).withMode(RollMode.ADVANTAGE).id());
    }

    @Test
    void constructorsWithoutAnIdStillWork() {
        HazardSave save = new HazardSave("Pit", DamageTypes.FALL, "k", 13, DICE, SuccessDamage.HALF, RollMode.NORMAL);
        assertEquals(Optional.empty(), save.id());
        HazardAttack attack = new HazardAttack("Darts", DamageTypes.ARROW, 5, DICE, RollMode.NORMAL);
        assertEquals(Optional.empty(), attack.id());
    }

    @Test
    void onlyAFlatZeroDealsNoDamage() {
        assertFalse(HazardSave.of("Gas", DamageTypes.MAGIC, "k", 13, HazardSave.NO_DAMAGE)
                .dealsDamage());
        assertFalse(HazardAttack.of("Needle", DamageTypes.ARROW, 5, DiceExpression.parse("0"))
                .dealsDamage());
        assertTrue(HazardSave.of("Gas", DamageTypes.MAGIC, "k", 13, DICE).dealsDamage());
        assertTrue(HazardSave.of("Gas", DamageTypes.MAGIC, "k", 13, DiceExpression.parse("1"))
                .dealsDamage());
        assertTrue(HazardSave.of("Gas", DamageTypes.MAGIC, "k", 13, DiceExpression.parse("1d4-5"))
                .dealsDamage());
    }
}
