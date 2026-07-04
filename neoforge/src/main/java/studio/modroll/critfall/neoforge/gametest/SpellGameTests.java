package studio.modroll.critfall.neoforge.gametest;

import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Husk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.RollService;
import studio.modroll.critfall.combat.FumbleCooldowns;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.data.EntityProfile;
import studio.modroll.critfall.data.ProfileStore;
import studio.modroll.critfall.data.SpellProfile;
import studio.modroll.critfall.dice.DiceRoller;

/**
 * End-to-end tests of M5 spell handling. Indirect living-caused damage (caster != direct entity,
 * the shape Iron's Spells / Ars Nouveau projectile spells produce) classifies as SPELL: without a
 * spell profile it falls back to an attack roll with derived dice; a profile can switch it to the
 * saving-throw resolution. The husk caster uses the zombies profile (+3), the pig target AC 10.
 */
@GameTestHolder(Critfall.MOD_ID)
@PrefixGameTestTemplate(false)
public class SpellGameTests {

    private static final String TEMPLATE = "empty";
    /** Vanilla spell damage 6.0 derives 1d12 damage dice (avg 6.5) when no spell profile matches. */
    private static final float SPELL_DAMAGE = 6.0F;

    private static final String SAVE_PROFILE =
            "{\"matches\": [\"minecraft:mob_attack\"], \"resolution\": \"save\", \"damage\": \"2d6\","
                    + " \"save\": {\"dc\": 13}}";

    @GameTest(template = TEMPLATE)
    public void indirectDamageRollsAsSpellWithDerivedDice(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        withRolls(
                helper,
                Rules.DEFAULTS,
                () -> {
                    // 13 + 3 (husk profile) = 16 vs AC 10 -> hit; derived 1d12 rolls a 9
                    pig.hurt(spellSource(helper, husk), SPELL_DAMAGE);
                    expectHealth(helper, pig, pig.getMaxHealth() - 9.0F);
                },
                13,
                9);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void spellRollsDisabledRestoresVanilla(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        Rules base = Rules.DEFAULTS;
        Rules rules = new Rules(
                new Rules.AttackRolls(true, true, true, true, false),
                base.damageDice(),
                base.crits(),
                base.fumbles(),
                base.spells(),
                base.fallbacks(),
                base.feedback(),
                base.balance());
        withRolls(helper, rules, () -> {
            pig.hurt(spellSource(helper, husk), SPELL_DAMAGE);
            expectHealth(helper, pig, pig.getMaxHealth() - SPELL_DAMAGE);
        });
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void unknownSpellPassthroughLeavesVanilla(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        Rules base = Rules.DEFAULTS;
        Rules rules = new Rules(
                base.attackRolls(),
                base.damageDice(),
                base.crits(),
                base.fumbles(),
                base.spells(),
                new Rules.Fallbacks(
                        Rules.FallbackMode.DERIVE, Rules.FallbackMode.DERIVE, Rules.FallbackMode.VANILLA_PASSTHROUGH),
                base.feedback(),
                base.balance());
        withRolls(helper, rules, () -> {
            pig.hurt(spellSource(helper, husk), SPELL_DAMAGE);
            expectHealth(helper, pig, pig.getMaxHealth() - SPELL_DAMAGE);
        });
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void successfulSaveHalvesTheDamage(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        withSpellProfile(
                SAVE_PROFILE,
                () -> withRolls(
                        helper,
                        Rules.DEFAULTS,
                        () -> {
                            // pig save 13 + 0 = 13 vs DC 13 succeeds; 2d6 roll 4+5 = 9 -> floor(9/2) = 4
                            pig.hurt(spellSource(helper, husk), SPELL_DAMAGE);
                            expectHealth(helper, pig, pig.getMaxHealth() - 4.0F);
                        },
                        13,
                        4,
                        5));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void failedSaveTakesFullProfileDice(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        withSpellProfile(
                SAVE_PROFILE,
                () -> withRolls(
                        helper,
                        Rules.DEFAULTS,
                        () -> {
                            // save 12 vs DC 13 fails; full 2d6 = 9 applies
                            pig.hurt(spellSource(helper, husk), SPELL_DAMAGE);
                            expectHealth(helper, pig, pig.getMaxHealth() - 9.0F);
                        },
                        12,
                        4,
                        5));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void negateOutcomeCancelsAllDamageOnSave(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        String profile = "{\"matches\": [\"minecraft:mob_attack\"], \"resolution\": \"save\", \"damage\": \"2d6\","
                + " \"save\": {\"dc\": 13, \"on_success\": \"negate\"}}";
        withSpellProfile(
                profile,
                () -> withRolls(
                        helper,
                        Rules.DEFAULTS,
                        () -> {
                            boolean hurt = pig.hurt(spellSource(helper, husk), SPELL_DAMAGE);
                            if (hurt || pig.getHealth() < pig.getMaxHealth()) {
                                helper.fail("a negated save must deal no damage");
                            }
                        },
                        13,
                        4,
                        5));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void targetProfileSaveBonusCounts(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        String nimblePig =
                "{\"matches\": [\"minecraft:pig\"], \"armor_class\": 10, \"save_bonus\": 5," + " \"priority\": 10}";
        withSpellProfile(
                SAVE_PROFILE,
                () -> withEntityProfile(
                        "critfall_test:nimble_pig",
                        nimblePig,
                        () -> withRolls(
                                helper,
                                Rules.DEFAULTS,
                                () -> {
                                    // save 8 + 5 = 13 vs DC 13 succeeds thanks to the profile bonus -> half of 9
                                    pig.hurt(spellSource(helper, husk), SPELL_DAMAGE);
                                    expectHealth(helper, pig, pig.getMaxHealth() - 4.0F);
                                },
                                8,
                                4,
                                5)));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void savesDisabledFallBackToAttackRoll(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        Rules base = Rules.DEFAULTS;
        Rules rules = new Rules(
                base.attackRolls(),
                base.damageDice(),
                base.crits(),
                base.fumbles(),
                new Rules.Spells(new Rules.SpellSaves(false, 13, Rules.SaveOutcome.HALF)),
                base.fallbacks(),
                base.feedback(),
                base.balance());
        withSpellProfile(
                SAVE_PROFILE,
                () -> withRolls(
                        helper,
                        rules,
                        () -> {
                            // saves off: the save-profile resolves as an attack roll with its 2d6 dice
                            // (13 + 3 = 16 vs AC 10 -> hit for 9); exactly one d20 is drawn
                            pig.hurt(spellSource(helper, husk), SPELL_DAMAGE);
                            expectHealth(helper, pig, pig.getMaxHealth() - 9.0F);
                        },
                        13,
                        4,
                        5));
        helper.succeed();
    }

    /**
     * A spell-shaped damage source: living caster, different direct entity — the construction
     * Iron's Spells and Ars Nouveau use for projectile/AoE spell damage (see docs/compat.md).
     * {@code mob_attack} is used as the damage type because vanilla ships no untagged spell type;
     * classification only cares that it is not exempt/projectile-tagged and not direct.
     */
    private static DamageSource spellSource(GameTestHelper helper, LivingEntity caster) {
        ArmorStand proxy = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(2, 1, 2));
        return helper.getLevel().damageSources().source(DamageTypes.MOB_ATTACK, proxy, caster);
    }

    private static <T extends Mob> T spawnCalm(GameTestHelper helper, EntityType<T> type, int x, int z) {
        T mob = helper.spawn(type, new BlockPos(x, 1, z));
        mob.setNoAi(true);
        return mob;
    }

    /** Adds one spell profile (id {@code critfall_test:spell}) for the duration of {@code action}. */
    private static void withSpellProfile(String json, Runnable action) {
        Map<ResourceLocation, SpellProfile> before = ProfileStore.spellProfiles();
        Map<ResourceLocation, SpellProfile> patched = new HashMap<>(before);
        ResourceLocation id = ResourceLocation.parse("critfall_test:spell");
        patched.put(id, SpellProfile.parse(id, JsonParser.parseString(json).getAsJsonObject(), warning -> {}));
        ProfileStore.setSpellProfiles(patched);
        try {
            action.run();
        } finally {
            ProfileStore.setSpellProfiles(before);
        }
    }

    private static void withEntityProfile(String id, String json, Runnable action) {
        Map<ResourceLocation, EntityProfile> before = ProfileStore.entityProfiles();
        Map<ResourceLocation, EntityProfile> patched = new HashMap<>(before);
        ResourceLocation profileId = ResourceLocation.parse(id);
        patched.put(
                profileId,
                EntityProfile.parse(profileId, JsonParser.parseString(json).getAsJsonObject(), warning -> {}));
        ProfileStore.setEntityProfiles(patched);
        try {
            action.run();
        } finally {
            ProfileStore.setEntityProfiles(before);
        }
    }

    private static void withRolls(GameTestHelper helper, Rules rules, Runnable action, int... faces) {
        ScriptedRandom scripted = ScriptedRandom.ofDieFaces(faces);
        Rules before = RollService.rules();
        RollService.setRoller(new DiceRoller(scripted));
        RollService.setRules(rules);
        FumbleCooldowns.clear();
        try {
            action.run();
            if (!scripted.isExhausted()) {
                helper.fail("fewer dice were rolled than scripted");
            }
        } finally {
            RollService.setRoller(new DiceRoller(new Random()));
            RollService.setRules(before);
            FumbleCooldowns.clear();
        }
    }

    private static void expectHealth(GameTestHelper helper, LivingEntity entity, float expected) {
        if (Math.abs(entity.getHealth() - expected) > 0.001F) {
            helper.fail("expected health " + expected + " but was " + entity.getHealth(), entity);
        }
    }
}
