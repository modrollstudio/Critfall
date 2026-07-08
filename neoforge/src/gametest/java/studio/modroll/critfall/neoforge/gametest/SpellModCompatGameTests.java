package studio.modroll.critfall.neoforge.gametest;

import com.google.gson.JsonParser;
import com.hollingsworth.arsnouveau.api.util.DamageUtil;
import com.hollingsworth.arsnouveau.setup.registry.DamageTypesRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.damage.ISSDamageTypes;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.random.RandomGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Husk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.RollRuntime;
import studio.modroll.critfall.combat.FumbleCooldowns;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.data.ProfileStore;
import studio.modroll.critfall.data.SpellProfile;
import studio.modroll.critfall.dice.DiceRoller;

/**
 * In-game verification of docs/compat.md against the REAL Iron's Spells 'n Spellbooks and Ars
 * Nouveau jars (loaded via localRuntime): damage sources are built through each mod's own factory,
 * so classification sees exactly what a live cast produces — their registered damage types, their
 * shipped tag data, and their direct/causing entity shapes. The husk caster resolves through the
 * zombies entity profile (+3), the pig target through the pig profile (AC 10) — same fixtures as
 * SpellScenarios.
 */
@GameTestHolder(Critfall.MOD_ID)
@PrefixGameTestTemplate(false)
public class SpellModCompatGameTests {

    private static final String TEMPLATE = "empty";
    /** Vanilla spell damage 6.0 derives 1d12 damage dice when no spell profile matches. */
    private static final float SPELL_DAMAGE = 6.0F;

    /** attack_rolls.spells = false, everything else (melee included!) still on. */
    private static final Rules SPELL_ROLLS_OFF = new Rules(
            new Rules.AttackRolls(true, true, true, true, false),
            Rules.DEFAULTS.damageDice(),
            Rules.DEFAULTS.crits(),
            Rules.DEFAULTS.fumbles(),
            Rules.DEFAULTS.spells(),
            Rules.DEFAULTS.fallbacks(),
            Rules.DEFAULTS.feedback(),
            Rules.DEFAULTS.balance());

    /**
     * Self-cast construction ({@code SpellDamageSource.source(caster, spell)}): the caster is both
     * direct and causing entity — the shape that would misroll as MELEE without the spell tag.
     * Expect SPELL: one d20 (13 + 3 = 16 vs AC 10, hit) then derived 1d12 rolling 9.
     */
    @GameTest(template = TEMPLATE)
    public void ironsSelfCastSpellRollsAsSpell(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        withRolls(
                helper,
                Rules.DEFAULTS,
                () -> {
                    pig.hurt(SpellDamageSource.source(husk, SpellRegistry.FIREBALL_SPELL.get()), SPELL_DAMAGE);
                    expectHealth(helper, pig, pig.getMaxHealth() - 9.0F);
                },
                13,
                9);
        helper.succeed();
    }

    /**
     * A save-resolution profile on the fireball's school tag: save 13 vs DC 13 → half of 2d6=9.
     * The tag id is derived from the spell's own school (every ISS school tag shares its id with
     * the school damage type) so the test tracks school reassignments across ISS versions —
     * 3.16.2 already moved Fireball from fire to evocation.
     */
    @GameTest(template = TEMPLATE)
    public void ironsSaveProfileResolvesAsSavingThrow(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        ArmorStand proxy = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(2, 1, 2));
        ResourceLocation schoolTag = SpellRegistry.FIREBALL_SPELL
                .get()
                .getSchoolType()
                .getDamageType()
                .location();
        String profile = "{\"matches\": [\"#" + schoolTag + "\"], \"resolution\": \"save\","
                + " \"damage\": \"2d6\", \"save\": {\"dc\": 13}}";
        withSpellProfile(
                profile,
                () -> withRolls(
                        helper,
                        Rules.DEFAULTS,
                        () -> {
                            // Projectile-spell construction: source(direct, caster, spell).
                            pig.hurt(
                                    SpellDamageSource.source(proxy, husk, SpellRegistry.FIREBALL_SPELL.get()),
                                    SPELL_DAMAGE);
                            expectHealth(helper, pig, pig.getMaxHealth() - 4.0F);
                        },
                        13,
                        4,
                        5));
        helper.succeed();
    }

    /**
     * Ground-AoE tick types must never roll. Worst-case shape: {@code fire_field} with a living
     * owner as BOTH direct and causing entity — only {@code #critfall:exempt} keeps this vanilla.
     */
    @GameTest(template = TEMPLATE)
    public void ironsFireFieldTickStaysVanilla(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        Holder<DamageType> fireField = helper.getLevel()
                .registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(ISSDamageTypes.FIRE_FIELD);
        withRolls(helper, Rules.DEFAULTS, () -> {
            pig.hurt(new DamageSource(fireField, husk, husk), SPELL_DAMAGE);
            expectHealth(helper, pig, pig.getMaxHealth() - SPELL_DAMAGE);
        });
        helper.succeed();
    }

    /**
     * attack_rolls.spells = false with melee rolls still ON: Iron's self-cast damage must pass
     * through vanilla untouched. A MELEE misclassification would draw dice and fail the script.
     */
    @GameTest(template = TEMPLATE)
    public void ironsSpellRollsOffRestoresVanilla(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        withRolls(helper, SPELL_ROLLS_OFF, () -> {
            pig.hurt(SpellDamageSource.source(husk, SpellRegistry.FIREBALL_SPELL.get()), SPELL_DAMAGE);
            expectHealth(helper, pig, pig.getMaxHealth() - SPELL_DAMAGE);
        });
        helper.succeed();
    }

    /** Self-cast construction ({@code DamageUtil.source(level, type, caster)}): direct == causing. */
    @GameTest(template = TEMPLATE)
    public void arsSelfCastSpellRollsAsSpell(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        withRolls(
                helper,
                Rules.DEFAULTS,
                () -> {
                    pig.hurt(
                            DamageUtil.source(helper.getLevel(), DamageTypesRegistry.GENERIC_SPELL_DAMAGE, husk),
                            SPELL_DAMAGE);
                    expectHealth(helper, pig, pig.getMaxHealth() - 9.0F);
                },
                13,
                9);
        helper.succeed();
    }

    /** A save-resolution profile on {@code ars_nouveau:spell}: save 13 vs DC 13 → half of 2d6=9. */
    @GameTest(template = TEMPLATE)
    public void arsSaveProfileResolvesAsSavingThrow(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        String profile = "{\"matches\": [\"ars_nouveau:spell\"], \"resolution\": \"save\","
                + " \"damage\": \"2d6\", \"save\": {\"dc\": 13}}";
        withSpellProfile(
                profile,
                () -> withRolls(
                        helper,
                        Rules.DEFAULTS,
                        () -> {
                            pig.hurt(
                                    DamageUtil.source(
                                            helper.getLevel(), DamageTypesRegistry.GENERIC_SPELL_DAMAGE, husk),
                                    SPELL_DAMAGE);
                            expectHealth(helper, pig, pig.getMaxHealth() - 4.0F);
                        },
                        13,
                        4,
                        5));
        helper.succeed();
    }

    /** {@code sourceberry_bush} is a repeating hazard — exempt, never rolls, even with an owner. */
    @GameTest(template = TEMPLATE)
    public void arsSourceberryBushStaysVanilla(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        withRolls(helper, Rules.DEFAULTS, () -> {
            pig.hurt(DamageUtil.source(helper.getLevel(), DamageTypesRegistry.SOURCE_BERRY_BUSH, husk), SPELL_DAMAGE);
            expectHealth(helper, pig, pig.getMaxHealth() - SPELL_DAMAGE);
        });
        helper.succeed();
    }

    /** The no-entity overload has no attacker: nobody to roll for, stays vanilla (docs/compat.md). */
    @GameTest(template = TEMPLATE)
    public void arsCasterlessSpellStaysVanilla(GameTestHelper helper) {
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        withRolls(helper, Rules.DEFAULTS, () -> {
            pig.hurt(DamageUtil.source(helper.getLevel(), DamageTypesRegistry.GENERIC_SPELL_DAMAGE), SPELL_DAMAGE);
            expectHealth(helper, pig, pig.getMaxHealth() - SPELL_DAMAGE);
        });
        helper.succeed();
    }

    /** attack_rolls.spells = false with melee rolls still ON: Ars self-cast damage stays vanilla. */
    @GameTest(template = TEMPLATE)
    public void arsSpellRollsOffRestoresVanilla(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        withRolls(helper, SPELL_ROLLS_OFF, () -> {
            pig.hurt(
                    DamageUtil.source(helper.getLevel(), DamageTypesRegistry.GENERIC_SPELL_DAMAGE, husk), SPELL_DAMAGE);
            expectHealth(helper, pig, pig.getMaxHealth() - SPELL_DAMAGE);
        });
        helper.succeed();
    }

    // Local copies of the SpellScenarios fixtures (those are package-private in common's gametest
    // source set; these tests are loader-specific and live here so common stays loader-agnostic).

    private static <T extends Mob> T spawnCalm(GameTestHelper helper, EntityType<T> type, int x, int z) {
        T mob = helper.spawn(type, new BlockPos(x, 1, z));
        mob.setNoAi(true);
        return mob;
    }

    /** Adds one spell profile (id {@code critfall_test:compat_spell}) for the duration of {@code action}. */
    private static void withSpellProfile(String json, Runnable action) {
        Map<ResourceLocation, SpellProfile> before = ProfileStore.spellProfiles();
        Map<ResourceLocation, SpellProfile> patched = new HashMap<>(before);
        ResourceLocation id = ResourceLocation.parse("critfall_test:compat_spell");
        patched.put(id, SpellProfile.parse(id, JsonParser.parseString(json).getAsJsonObject(), warning -> {}));
        ProfileStore.setSpellProfiles(patched);
        try {
            action.run();
        } finally {
            ProfileStore.setSpellProfiles(before);
        }
    }

    private static void withRolls(GameTestHelper helper, Rules rules, Runnable action, int... faces) {
        ScriptedFaces scripted = new ScriptedFaces(faces);
        Rules before = RollRuntime.rules();
        RollRuntime.setRoller(new DiceRoller(scripted));
        RollRuntime.setRules(rules);
        FumbleCooldowns.clear();
        try {
            action.run();
            if (!scripted.isExhausted()) {
                helper.fail("fewer dice were rolled than scripted");
            }
        } finally {
            RollRuntime.setRoller(new DiceRoller(new Random()));
            RollRuntime.setRules(before);
            FumbleCooldowns.clear();
        }
    }

    private static void expectHealth(GameTestHelper helper, LivingEntity entity, float expected) {
        if (Math.abs(entity.getHealth() - expected) > 0.001F) {
            helper.fail("expected health " + expected + " but was " + entity.getHealth(), entity);
        }
    }

    /** Forces exact die faces; throws if more dice are drawn than scripted (the misroll detector). */
    private static final class ScriptedFaces implements RandomGenerator {

        private final Queue<Integer> faces = new ArrayDeque<>();

        private ScriptedFaces(int... faces) {
            for (int face : faces) {
                this.faces.add(face);
            }
        }

        @Override
        public int nextInt(int bound) {
            Integer face = faces.poll();
            if (face == null) {
                throw new IllegalStateException("ScriptedFaces exhausted: more rolls happened than were scripted");
            }
            if (face < 1 || face > bound) {
                throw new IllegalStateException("forced face " + face + " is impossible for a d" + bound);
            }
            return face - 1;
        }

        @Override
        public long nextLong() {
            throw new IllegalStateException("dice rolling must go through nextInt(bound)");
        }

        boolean isExhausted() {
            return faces.isEmpty();
        }
    }
}
