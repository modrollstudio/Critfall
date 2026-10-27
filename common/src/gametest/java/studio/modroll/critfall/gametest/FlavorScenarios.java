package studio.modroll.critfall.gametest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.random.RandomGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Husk;
import studio.modroll.critfall.RollRuntime;
import studio.modroll.critfall.api.dice.DiceRoller;
import studio.modroll.critfall.api.feedback.FlavorName;
import studio.modroll.critfall.api.feedback.RollFeedbackPayload;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.data.FlavorPool;
import studio.modroll.critfall.data.ProfileStore;
import studio.modroll.critfall.feedback.CapturingFeedbackSink;
import studio.modroll.critfall.feedback.FeedbackSink;
import studio.modroll.critfall.feedback.FlavorCooldowns;
import studio.modroll.critfall.feedback.FlavorKills;
import studio.modroll.critfall.feedback.FlavorText;

/**
 * Flavor lines in game: a dimension's own pools replace the normal ones there and nowhere else, and
 * every shipped line reads as finished text once the names are filled in.
 */
public final class FlavorScenarios {

    private static final String DIMENSION_LINE = "critfall.gametest.flavor.dimension_kill";

    private FlavorScenarios() {}

    /**
     * Runs {@code action} with every flavor pick taking the first line ({@code last} false) or the last.
     * The matched pool's own lines come first and the base lines last.
     */
    static void withFlavorPick(boolean last, Runnable action) {
        RollRuntime.setFeedbackRoller(new DiceRoller(new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0L;
            }

            @Override
            public int nextInt(int bound) {
                return last ? bound - 1 : 0;
            }
        }));
        try {
            action.run();
        } finally {
            RollRuntime.setFeedbackRoller(new DiceRoller(new Random()));
        }
    }

    /** A base pool for the test's own dimension gives the kill line, ahead of the shipped pools. */
    public static void dimensionLinesReplaceTheNormalPools(GameTestHelper helper) {
        ResourceLocation here = helper.getLevel().dimension().location();
        withDimensionPool(here, () -> {
            for (boolean last : new boolean[] {false, true}) {
                RollFeedbackPayload payload = huskKill(helper, last);
                if (payload.flavorKey().isEmpty() || !payload.flavorKey().get().equals(DIMENSION_LINE)) {
                    helper.fail("a kill in " + here + " must use its own line, was " + payload.flavorKey());
                }
            }
        });
        helper.succeed();
    }

    /** A pool for some other dimension changes nothing here: the kill reads from the shipped base pool. */
    public static void otherDimensionsUseTheBasePools(GameTestHelper helper) {
        ResourceLocation elsewhere = ResourceLocation.fromNamespaceAndPath("critfall_test", "elsewhere");
        withDimensionPool(elsewhere, () -> {
            RollFeedbackPayload payload = huskKill(helper, true);
            String key = payload.flavorKey().orElse("");
            if (!key.startsWith("critfall.flavor.base.kill.")) {
                helper.fail("a kill outside " + elsewhere + " must fall back to the base pool, was " + key);
            }
            payload = huskKill(helper, false);
            key = payload.flavorKey().orElse("");
            if (!key.startsWith("critfall.flavor.default.kill.")) {
                helper.fail("an unarmed husk's kill must still lead with the unarmed pool, was " + key);
            }
        });
        helper.succeed();
    }

    /** Every loaded line, filled with real entity names, has no placeholder, brace or raw key left. */
    /**
     * About 1 in 4 ordinary kills get a line ({@code kill_chance_percent} 25): the cosmetic d100 decides
     * before the line is picked, and the combat dice are never touched.
     */
    public static void ordinaryKillsGetALineAtTheKillChance(GameTestHelper helper) {
        RollFeedbackPayload missed = scriptedKill(helper, Rules.DEFAULTS, null, 26);
        if (missed.flavorKey().isPresent()) {
            helper.fail("a d100 of 26 is outside the 25% kill chance, but the kill got " + missed.flavorKey());
        }
        RollFeedbackPayload shown = scriptedKill(helper, Rules.DEFAULTS, null, 25, 1);
        if (shown.flavorKey().isEmpty() || !shown.flavorKey().get().contains(".kill.")) {
            helper.fail("a d100 of 25 is inside the 25% kill chance, but the kill got " + shown);
        }
        helper.succeed();
    }

    /** A named mob (a name tag, a Depths elite or boss) and a boss always get their killing-blow line. */
    public static void namedMobsAndBossesAlwaysGetAKillLine(GameTestHelper helper) {
        // chance 0: an ordinary kill never gets a line, and no d100 is drawn for a named one
        RollFeedbackPayload named =
                scriptedKill(helper, CombatScenarios.withKillChance(Rules.DEFAULTS, 0), "Varkas, the Unbroken", 1);
        if (named.flavorKey().isEmpty()) {
            helper.fail("a named mob's killing blow must always get a line, was " + named);
        }
        RollFeedbackPayload ordinary = scriptedKill(helper, CombatScenarios.withKillChance(Rules.DEFAULTS, 0), null);
        if (ordinary.flavorKey().isPresent()) {
            helper.fail("with the chance at 0 an ordinary kill gets no line, was " + ordinary.flavorKey());
        }
        for (EntityType<?> boss : List.of(EntityType.WITHER, EntityType.ENDER_DRAGON)) {
            if (!(boss.create(helper.getLevel()) instanceof LivingEntity living)
                    || !FlavorKills.alwaysFlavored(living)) {
                helper.fail(boss.getDescriptionId() + " must be in #c:bosses and always get a kill line");
            }
        }
        if (FlavorKills.alwaysFlavored(EntityType.PIG.create(helper.getLevel()))) {
            helper.fail("an unnamed pig is an ordinary kill");
        }
        helper.succeed();
    }

    /**
     * A husk's lethal unarmed hit on a pig, named {@code name} unless null, with the cosmetic roller
     * scripted to {@code flavorFaces} (the d100 kill chance, then the line pick).
     */
    private static RollFeedbackPayload scriptedKill(
            GameTestHelper helper, Rules rules, String name, int... flavorFaces) {
        CapturingFeedbackSink sink = new CapturingFeedbackSink();
        FeedbackSink previous = FeedbackSink.get();
        FeedbackSink.set(sink);
        FlavorCooldowns.clear();
        ScriptedRandom flavor = ScriptedRandom.ofDieFaces(flavorFaces);
        RollRuntime.setFeedbackRoller(new DiceRoller(flavor));
        try {
            Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
            Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
            if (name != null) {
                pig.setCustomName(Component.literal(name));
            }
            pig.setHealth(3.0F);
            // d20 13 + 3 vs AC 10 hits, 1d6+1 rolls 6 for 7 damage
            CombatScenarios.withRolls(
                    helper,
                    rules,
                    () -> pig.hurt(helper.getLevel().damageSources().mobAttack(husk), 3.0F),
                    13,
                    6);
            RollFeedbackPayload payload = sink.lastRoll();
            if (payload == null || !pig.isDeadOrDying()) {
                helper.fail("the husk should have killed the pig, payload " + payload);
            }
            if (!flavor.isExhausted()) {
                helper.fail("fewer cosmetic dice were drawn than scripted");
            }
            husk.discard();
            return payload;
        } finally {
            RollRuntime.setFeedbackRoller(new DiceRoller(new Random()));
            FeedbackSink.set(previous);
        }
    }

    public static void everyShippedLineFillsItsPlaceholders(GameTestHelper helper) {
        Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        List<FlavorName[]> names = List.of(
                new FlavorName[] {FlavorName.of(husk), FlavorName.of(pig)},
                new FlavorName[] {FlavorName.literal("Alex"), FlavorName.of(husk)});
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (FlavorPool pool : ProfileStore.flavorPools().values()) {
            for (String outcome : FlavorPool.OUTCOMES) {
                for (String key : pool.lines(outcome)) {
                    if (!Language.getInstance().has(key)) {
                        problems.add(key + " has no text");
                        continue;
                    }
                    for (FlavorName[] pair : names) {
                        String line = FlavorText.line(key, pair[0], pair[1]).getString();
                        if (line.isBlank()
                                || line.contains("{")
                                || line.contains("}")
                                || line.contains("critfall.")
                                || line.contains("entity.minecraft.")) {
                            problems.add(key + " reads \"" + line + "\"");
                        }
                    }
                    checked++;
                }
            }
        }
        if (!problems.isEmpty()) {
            helper.fail(problems.size() + " flavor lines do not read right, first: " + problems.getFirst());
        }
        if (checked < 120) {
            helper.fail("expected at least 120 shipped flavor lines, found " + checked);
        }
        String sample = FlavorText.line("critfall.flavor.base.kill.0", FlavorName.of(husk), FlavorName.of(pig))
                .getString();
        if (!sample.contains("Pig")) {
            helper.fail("the target's name should read as its type name, was \"" + sample + "\"");
        }
        helper.succeed();
    }

    /** A husk's lethal unarmed hit on a pig; returns the dispatched payload after checking its names. */
    private static RollFeedbackPayload huskKill(GameTestHelper helper, boolean lastLine) {
        CapturingFeedbackSink sink = new CapturingFeedbackSink();
        FeedbackSink previous = FeedbackSink.get();
        FeedbackSink.set(sink);
        FlavorCooldowns.clear();
        try {
            Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
            Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
            pig.setHealth(3.0F);
            RollFeedbackPayload[] dispatched = new RollFeedbackPayload[1];
            withFlavorPick(
                    lastLine,
                    // d20 13 + 3 vs AC 10 hits, 1d6+1 rolls 6 for 7 damage
                    () -> CombatScenarios.withRolls(
                            helper,
                            CombatScenarios.EVERY_KILL_LINE,
                            () -> {
                                pig.hurt(helper.getLevel().damageSources().mobAttack(husk), 3.0F);
                                dispatched[0] = sink.lastRoll();
                            },
                            13,
                            6));
            RollFeedbackPayload payload = dispatched[0];
            if (payload == null || !pig.isDeadOrDying()) {
                helper.fail("the husk should have killed the pig, payload " + payload);
            }
            if (!payload.flavorAttacker().equals(FlavorName.of(husk))
                    || !payload.flavorTarget().equals(FlavorName.of(pig))) {
                helper.fail("the kill line must carry both names, was " + payload.flavorAttacker() + " and "
                        + payload.flavorTarget());
            }
            husk.discard();
            return payload;
        } finally {
            FeedbackSink.set(previous);
        }
    }

    /** Runs {@code action} with one extra base pool for {@code dimension} loaded, then restores the pools. */
    private static void withDimensionPool(ResourceLocation dimension, Runnable action) {
        Map<ResourceLocation, FlavorPool> before = ProfileStore.flavorPools();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("critfall_test", "dimension_base");
        Map<ResourceLocation, FlavorPool> pools = new HashMap<>(before);
        pools.put(
                id,
                new FlavorPool(
                        id,
                        List.of(),
                        java.util.Set.of(),
                        Map.of(FlavorPool.KILL, List.of(DIMENSION_LINE)),
                        0,
                        true,
                        true,
                        Optional.of(dimension)));
        ProfileStore.setFlavorPools(pools);
        try {
            action.run();
        } finally {
            ProfileStore.setFlavorPools(before);
        }
    }
}
