package studio.modroll.critfall.gametest;

import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import studio.modroll.critfall.api.RollService;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.data.EntityProfile;
import studio.modroll.critfall.data.ProfileStore;

/**
 * Per-entity profiles: one husk gets its own profile without changing every husk. Shipped profiles: husk
 * AC 9 and +3 with 1d4+1, pig AC 10.
 */
public final class EntityProfileScenarios {

    static final ResourceLocation DUNGEON_HUSK = ResourceLocation.parse("critfall_test:dungeon_husk");
    private static final String DUNGEON_HUSK_JSON = "{\"matches\": [], \"armor_class\": 17, \"attack_bonus\": 7}";

    private EntityProfileScenarios() {}

    public static void assignedProfileBeatsTheTypeProfile(GameTestHelper helper) {
        Husk dungeonHusk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
        Husk plainHusk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 3, 1);
        withProfile(DUNGEON_HUSK, DUNGEON_HUSK_JSON, () -> {
            RollService.setEntityProfile(dungeonHusk, DUNGEON_HUSK);
            expectArmorClass(helper, dungeonHusk, 17, "the assigned profile");
            expectArmorClass(helper, plainHusk, 9, "another husk keeps the type's profile");
            if (!RollService.entityProfile(dungeonHusk).equals(Optional.of(DUNGEON_HUSK))) {
                helper.fail(
                        "entityProfile must read back the assigned id, was " + RollService.entityProfile(dungeonHusk));
            }
            RollService.clearEntityProfile(dungeonHusk);
            expectArmorClass(helper, dungeonHusk, 9, "a cleared assignment");
            if (RollService.entityProfile(dungeonHusk).isPresent()) {
                helper.fail("clearEntityProfile must remove the assignment");
            }
        });
        helper.succeed();
    }

    /** The automatic pipeline rolls against the assigned profile's AC, not the type's. */
    public static void assignedProfileDrivesTheLiveRoll(GameTestHelper helper) {
        Husk attacker = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
        Husk target = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 3, 3);
        withProfile(DUNGEON_HUSK, DUNGEON_HUSK_JSON, () -> {
            RollService.setEntityProfile(target, DUNGEON_HUSK);
            CombatScenarios.withRolls(
                    helper,
                    Rules.DEFAULTS,
                    () -> {
                        // 13 + 3 = 16 hits the husk's own AC 9 but misses the assigned AC 17.
                        target.hurt(helper.getLevel().damageSources().mobAttack(attacker), 3.0F);
                        if (target.getHealth() != target.getMaxHealth()) {
                            helper.fail("16 vs the assigned AC 17 must miss, health was " + target.getHealth());
                        }
                    },
                    13);
        });
        helper.succeed();
    }

    public static void assignedProfileIsSavedWithTheEntity(GameTestHelper helper) {
        Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
        RollService.setEntityProfile(husk, DUNGEON_HUSK);
        CompoundTag saved = husk.saveWithoutId(new CompoundTag());
        if (!DUNGEON_HUSK.toString().equals(saved.getString("CritfallProfile"))) {
            helper.fail("the assignment must be saved as CritfallProfile, NBT was " + saved);
        }
        Husk loaded = EntityType.HUSK.create(helper.getLevel());
        loaded.load(saved);
        if (!RollService.entityProfile(loaded).equals(Optional.of(DUNGEON_HUSK))) {
            helper.fail("a reloaded husk must keep its profile, was " + RollService.entityProfile(loaded));
        }
        saved.remove("CritfallProfile");
        loaded.load(saved);
        if (RollService.entityProfile(loaded).isPresent()) {
            helper.fail("loading data without CritfallProfile must clear the assignment");
        }
        helper.succeed();
    }

    public static void summonDataAssignsTheProfile(GameTestHelper helper) {
        Vec3 at = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
        int result = CommandScenarios.execute(
                helper,
                CommandScenarios.serverSource(helper).withLevel(helper.getLevel()),
                // Plain decimals: GameTest positions are far out, and a double prints them as 1.4E7.
                String.format(Locale.ROOT, "summon minecraft:husk %.2f %.2f %.2f", at.x, at.y, at.z)
                        + " {NoAI:1b,Tags:[\"critfall_summoned\"],CritfallProfile:\"" + DUNGEON_HUSK + "\"}");
        if (result != 1) {
            helper.fail("/summon must succeed, returned " + result);
        }
        List<Husk> summoned = helper.getLevel()
                .getEntitiesOfClass(Husk.class, new AABB(BlockPos.containing(at)).inflate(2), husk -> husk.getTags()
                        .contains("critfall_summoned"));
        if (summoned.size() != 1) {
            helper.fail("expected one summoned husk, found " + summoned.size());
            return;
        }
        Husk husk = summoned.getFirst();
        withProfile(DUNGEON_HUSK, DUNGEON_HUSK_JSON, () -> expectArmorClass(helper, husk, 17, "a /summon assignment"));
        husk.discard();
        helper.succeed();
    }

    public static void unloadedAssignedProfileFallsBackToTheType(GameTestHelper helper) {
        Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 1, 1);
        ResourceLocation missing = ResourceLocation.parse("critfall_test:not_loaded");
        RollService.setEntityProfile(pig, missing);
        expectArmorClass(helper, pig, 10, "an assigned profile that is not loaded");
        if (!RollService.entityProfile(pig).equals(Optional.of(missing))) {
            helper.fail("the assignment is kept while its profile is not loaded");
        }
        helper.succeed();
    }

    /** Adds one entity profile for the duration of {@code action}. */
    static void withProfile(ResourceLocation id, String json, Runnable action) {
        Map<ResourceLocation, EntityProfile> before = ProfileStore.entityProfiles();
        Map<ResourceLocation, EntityProfile> patched = new HashMap<>(before);
        patched.put(id, EntityProfile.parse(id, JsonParser.parseString(json).getAsJsonObject(), warning -> {}));
        ProfileStore.setEntityProfiles(patched);
        try {
            action.run();
        } finally {
            ProfileStore.setEntityProfiles(before);
        }
    }

    private static void expectArmorClass(GameTestHelper helper, LivingEntity entity, int expected, String what) {
        int actual = RollService.effectiveEntity(entity).armorClass();
        if (actual != expected) {
            helper.fail(what + ": expected AC " + expected + ", was " + actual);
        }
    }
}
