package studio.modroll.critfall.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import studio.modroll.critfall.data.EntityProfile;
import studio.modroll.critfall.data.ItemProfile;
import studio.modroll.critfall.dice.DiceExpression;

class AttackDiceTest {

    private static ItemProfile item(String json) {
        return ItemProfile.parse(
                ResourceLocation.parse("test:item"),
                JsonParser.parseString(json).getAsJsonObject(),
                warning -> {});
    }

    private static EntityProfile entity(String json) {
        return EntityProfile.parse(
                ResourceLocation.parse("test:entity"),
                JsonParser.parseString(json).getAsJsonObject(),
                warning -> {});
    }

    private static final EntityProfile ZOMBIE =
            entity("{\"matches\": [\"minecraft:zombie\"], \"damage\": {\"melee\": \"1d6+1\"}, \"crit_range\": 19}");

    @Test
    void weaponDiceGainAttributeBonus() {
        ItemProfile sword = item("{\"matches\": [\"#minecraft:swords\"], \"damage\": \"1d8\"}");
        // iron sword: attack damage 6, dice average 4.5 -> bonus round(1.5) = 2
        AttackDice.Resolved resolved =
                AttackDice.resolve(Optional.of(sword), Optional.empty(), 6.0).orElseThrow();
        assertEquals("1d8+2", resolved.dice().toString());
        assertEquals(20, resolved.critRange());
    }

    @Test
    void modifierFromNoneUsesDiceVerbatim() {
        ItemProfile sword =
                item("{\"matches\": [\"#minecraft:swords\"], \"damage\": \"1d8\", \"modifier_from\": \"none\"}");
        AttackDice.Resolved resolved =
                AttackDice.resolve(Optional.of(sword), Optional.empty(), 6.0).orElseThrow();
        assertEquals("1d8", resolved.dice().toString());
    }

    @Test
    void weakWeaponNeverGetsNegativeBonus() {
        ItemProfile stick = item("{\"matches\": [\"minecraft:stick\"], \"damage\": \"2d6\"}");
        AttackDice.Resolved resolved =
                AttackDice.resolve(Optional.of(stick), Optional.empty(), 1.0).orElseThrow();
        assertEquals("2d6", resolved.dice().toString());
    }

    @Test
    void weaponCritRangeBeatsEntityCritRange() {
        ItemProfile keen = item("{\"matches\": [\"minecraft:iron_sword\"], \"damage\": \"1d8\", \"crit_range\": 18}");
        AttackDice.Resolved resolved =
                AttackDice.resolve(Optional.of(keen), Optional.of(ZOMBIE), 6.0).orElseThrow();
        assertEquals(18, resolved.critRange());
    }

    @Test
    void weaponWithoutOwnCritRangeInheritsEntityCritRange() {
        ItemProfile sword = item("{\"matches\": [\"#minecraft:swords\"], \"damage\": \"1d8\"}");
        AttackDice.Resolved resolved =
                AttackDice.resolve(Optional.of(sword), Optional.of(ZOMBIE), 6.0).orElseThrow();
        assertEquals(19, resolved.critRange());
    }

    @Test
    void entityMeleeDiceUsedWhenNoWeaponProfile() {
        AttackDice.Resolved resolved =
                AttackDice.resolve(Optional.empty(), Optional.of(ZOMBIE), 3.0).orElseThrow();
        assertEquals("1d6+1", resolved.dice().toString());
        assertEquals(19, resolved.critRange());
    }

    @Test
    void emptyWhenNeitherProfileProvidesDice() {
        EntityProfile acOnly = entity("{\"matches\": [\"minecraft:pig\"], \"armor_class\": 10}");
        assertTrue(
                AttackDice.resolve(Optional.empty(), Optional.of(acOnly), 3.0).isEmpty());
        assertTrue(AttackDice.resolve(Optional.empty(), Optional.empty(), 3.0).isEmpty());
    }

    private static final EntityProfile GHAST =
            entity("{\"matches\": [\"minecraft:ghast\"], \"damage\": {\"ranged\": \"2d6\"}, \"crit_range\": 19}");

    @Test
    void launcherDiceGainBonusFromVanillaProjectileDamage() {
        ItemProfile bow = item("{\"matches\": [\"minecraft:bow\"], \"damage\": \"1d8\"}");
        // full-draw arrow: vanilla ~6, dice average 4.5 -> bonus round(1.5) = 2
        AttackDice.Resolved resolved = AttackDice.resolveRanged(
                        Optional.of(bow), Optional.empty(), Optional.empty(), 6.0)
                .orElseThrow();
        assertEquals("1d8+2", resolved.dice().toString());
        assertEquals(20, resolved.critRange());
    }

    @Test
    void ammoProfileDiceAreAddedOnTop() {
        ItemProfile bow = item("{\"matches\": [\"minecraft:bow\"], \"damage\": \"1d8\", \"modifier_from\": \"none\"}");
        AttackDice.Resolved resolved = AttackDice.resolveRanged(
                        Optional.of(bow), Optional.of(DiceExpression.parse("1d4")), Optional.empty(), 6.0)
                .orElseThrow();
        assertEquals("1d8+1d4", resolved.dice().toString());
    }

    @Test
    void entityRangedDiceUsedWhenNoLauncherProfile() {
        AttackDice.Resolved resolved = AttackDice.resolveRanged(
                        Optional.empty(), Optional.empty(), Optional.of(GHAST), 6.0)
                .orElseThrow();
        assertEquals("2d6", resolved.dice().toString());
        assertEquals(19, resolved.critRange());
    }

    @Test
    void launcherCritRangeBeatsEntityCritRange() {
        ItemProfile sniper = item("{\"matches\": [\"minecraft:bow\"], \"damage\": \"1d8\", \"crit_range\": 18}");
        AttackDice.Resolved resolved = AttackDice.resolveRanged(
                        Optional.of(sniper), Optional.empty(), Optional.of(GHAST), 6.0)
                .orElseThrow();
        assertEquals(18, resolved.critRange());
    }

    @Test
    void emptyWhenNeitherLauncherNorEntityProvidesRangedDice() {
        EntityProfile meleeOnly = entity("{\"matches\": [\"minecraft:zombie\"], \"damage\": {\"melee\": \"1d6\"}}");
        assertTrue(AttackDice.resolveRanged(Optional.empty(), Optional.empty(), Optional.of(meleeOnly), 6.0)
                .isEmpty());
        assertTrue(AttackDice.resolveRanged(Optional.empty(), Optional.empty(), Optional.empty(), 6.0)
                .isEmpty());
    }

    @Test
    void itemDamageBonusDerivation() {
        assertEquals(2, Derivation.itemDamageBonus(6.0, 4.5), "iron sword over 1d8");
        assertEquals(4, Derivation.itemDamageBonus(8.0, 4.5), "netherite sword over 1d8");
        assertEquals(0, Derivation.itemDamageBonus(1.0, 4.5), "never negative");
        assertEquals(12, Derivation.itemDamageBonus(99.0, 4.5), "clamped at +12");
    }
}
