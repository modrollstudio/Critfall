package studio.modroll.critfall.gametest;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import studio.modroll.critfall.api.ModifierProvider;
import studio.modroll.critfall.api.RollService;
import studio.modroll.critfall.api.combat.AttackOutcome;
import studio.modroll.critfall.api.combat.SaveResult;
import studio.modroll.critfall.api.dice.RollMode;
import studio.modroll.critfall.api.feedback.CheckFeedbackPayload;
import studio.modroll.critfall.api.feedback.ConsequenceLine;
import studio.modroll.critfall.api.feedback.RollFeedbackPayload;
import studio.modroll.critfall.combat.CombatText;
import studio.modroll.critfall.combat.CritfallTags;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.feedback.CapturingFeedbackSink;
import studio.modroll.critfall.feedback.FeedbackSink;
import studio.modroll.critfall.feedback.SaveFeedbackPayload;

/**
 * The modless fallback must stay legible: a vanilla-client player still learns WHICH consequence
 * fired, not just the bare roll. Driven through the real damage handler with scripted RNG. A
 * loader-agnostic {@link CapturingFeedbackSink} records what the pipeline dispatched, so the same
 * body asserts on both loaders (the loader dispatchers are exercised by their own smoke paths).
 */
public final class FeedbackScenarios {

    private FeedbackScenarios() {}

    public static void modlessFallbackCarriesConsequenceText(GameTestHelper helper) {
        CapturingFeedbackSink sink = new CapturingFeedbackSink();
        FeedbackSink previous = FeedbackSink.get();
        FeedbackSink.set(sink);
        try {
            Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
            Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
            husk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            CombatScenarios.withRolls(
                    helper,
                    CombatScenarios.WEAPON_DAMAGE,
                    () -> {
                        // nat 1, confirmation 2 < DC 10 fails -> fumble confirmed, table pick 1 = durability
                        pig.hurt(helper.getLevel().damageSources().mobAttack(husk), 3.0F);
                        RollFeedbackPayload payload = sink.lastRoll();
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
        } finally {
            FeedbackSink.set(previous);
        }
    }

    public static void critDispatchesCritPayload(GameTestHelper helper) {
        CapturingFeedbackSink sink = new CapturingFeedbackSink();
        FeedbackSink previous = FeedbackSink.get();
        FeedbackSink.set(sink);
        try {
            Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
            Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
            CombatScenarios.withRolls(
                    helper,
                    Rules.DEFAULTS,
                    () -> {
                        pig.hurt(helper.getLevel().damageSources().mobAttack(husk), 3.0F); // nat 20 -> crit
                        RollFeedbackPayload payload = sink.lastRoll();
                        if (payload == null || payload.outcome() != AttackOutcome.CRIT) {
                            helper.fail("nat 20 should dispatch a CRIT payload, was " + payload);
                        }
                    },
                    20);
            helper.succeed();
        } finally {
            FeedbackSink.set(previous);
        }
    }

    /** An add-on's save, rolled through Critfall, shown to the entity that rolled it. */
    public static void addOnSaveReadoutGoesToTheRoller(GameTestHelper helper) {
        CapturingFeedbackSink sink = new CapturingFeedbackSink();
        FeedbackSink previous = FeedbackSink.get();
        FeedbackSink.set(sink);
        try {
            Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
            CombatScenarios.withRolls(
                    helper,
                    Rules.DEFAULTS,
                    () -> {
                        SaveResult save = RollService.savingThrow(pig, 2, 13);
                        RollService.sendCheckFeedback(pig, CheckFeedbackPayload.save("Pressure plate", save));
                        expectCheckReadout(helper, sink, pig, "Pressure plate: save d20 12+2=14 vs DC 13 — SAVED");
                    },
                    12);
            helper.succeed();
        } finally {
            FeedbackSink.set(previous);
        }
    }

    /** An add-on's check under advantage shows both dice, as Critfall's own readout does. */
    public static void addOnCheckReadoutShowsAdvantage(GameTestHelper helper) {
        CapturingFeedbackSink sink = new CapturingFeedbackSink();
        FeedbackSink previous = FeedbackSink.get();
        FeedbackSink.set(sink);
        try {
            Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
            CombatScenarios.withRolls(
                    helper,
                    Rules.DEFAULTS,
                    () -> {
                        SaveResult check = RollService.savingThrow(pig, 3, 15, RollMode.ADVANTAGE);
                        RollService.sendCheckFeedback(pig, CheckFeedbackPayload.check("Perception", check));
                        expectCheckReadout(
                                helper, sink, pig, "Perception: check d20 adv 6/11 → 11+3=14 vs DC 15 — FAILED");
                    },
                    6,
                    11);
            helper.succeed();
        } finally {
            FeedbackSink.set(previous);
        }
    }

    /** A hidden check (a search) shows the roll and total, but no DC and no result. */
    public static void addOnHiddenCheckShowsNoDcOrResult(GameTestHelper helper) {
        CapturingFeedbackSink sink = new CapturingFeedbackSink();
        FeedbackSink previous = FeedbackSink.get();
        FeedbackSink.set(sink);
        try {
            Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
            CombatScenarios.withRolls(
                    helper,
                    Rules.DEFAULTS,
                    () -> {
                        SaveResult search = RollService.savingThrow(pig, 3, 15);
                        RollService.sendCheckFeedback(pig, CheckFeedbackPayload.hiddenCheck("Search", search));
                        expectCheckReadout(helper, sink, pig, "Search: check d20 14+3=17");
                        CheckFeedbackPayload payload = sink.lastCheck();
                        if (payload.dc() != 0 || payload.success()) {
                            helper.fail("a hidden check must not carry its DC or result, was " + payload);
                        }
                    },
                    14);
            helper.succeed();
        } finally {
            FeedbackSink.set(previous);
        }
    }

    /** An add-on's save named by its save key reads "DEX save". */
    public static void addOnSaveReadoutNamesTheSave(GameTestHelper helper) {
        CapturingFeedbackSink sink = new CapturingFeedbackSink();
        FeedbackSink previous = FeedbackSink.get();
        FeedbackSink.set(sink);
        try {
            Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
            CombatScenarios.withRolls(
                    helper,
                    Rules.DEFAULTS,
                    () -> {
                        SaveResult save = RollService.savingThrow(pig, 2, 13);
                        RollService.sendCheckFeedback(
                                pig,
                                CheckFeedbackPayload.save("Pressure plate", ModifierProvider.DEXTERITY_SAVE, save));
                        expectCheckReadout(helper, sink, pig, "Pressure plate: DEX save d20 12+2=14 vs DC 13 — SAVED");
                    },
                    12);
            helper.succeed();
        } finally {
            FeedbackSink.set(previous);
        }
    }

    private static void expectCheckReadout(
            GameTestHelper helper, CapturingFeedbackSink sink, LivingEntity roller, String expected) {
        CheckFeedbackPayload payload = sink.lastCheck();
        if (payload == null || sink.lastCheckRoller() != roller) {
            helper.fail("the readout must be sent for the roller, was " + payload + " for " + sink.lastCheckRoller());
            return;
        }
        String readout = CombatText.actionBar(payload).getString();
        if (!readout.equals(expected)) {
            helper.fail("expected \"" + expected + "\", was \"" + readout + "\"");
        }
    }

    /**
     * The readout follows the roll with what the target actually took: a husk's 5 (d20 13 + 3 vs AC
     * 10, 1d4+1 rolls 4) on a pig with Resistance III, which takes 60% off, leaves 2.
     */
    public static void readoutShowsDamageTakenAfterResistance(GameTestHelper helper) {
        hitReadout(
                helper,
                pig -> pig.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 2)),
                4,
                2.0F,
                "HIT 1d4+1 = 5 → 2");
    }

    /** Nothing changed the roll: the readout shows it once, with no arrow. */
    public static void readoutShowsAnUnchangedHitOnce(GameTestHelper helper) {
        hitReadout(helper, pig -> {}, 4, 5.0F, "HIT 1d4+1 = 5");
    }

    /** A kill shows the whole hit, not just the health the target had left. */
    public static void readoutShowsTheWholeKillingBlow(GameTestHelper helper) {
        hitReadout(helper, pig -> pig.setHealth(LOW_HEALTH), 4, 5.0F, "HIT 1d4+1 = 5");
    }

    /** Hard adds half to a mob's hit on a player: the husk's 5 (1d4+1 rolls 4) is 7.5 taken. */
    public static void readoutShowsDifficultyScaledDamageTaken(GameTestHelper helper) {
        CapturingFeedbackSink sink = new CapturingFeedbackSink();
        FeedbackSink previous = FeedbackSink.get();
        FeedbackSink.set(sink);
        try {
            Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            CombatScenarios.withDifficulty(
                    helper,
                    Difficulty.HARD,
                    () -> CombatScenarios.withRolls(
                            helper,
                            Rules.DEFAULTS,
                            () -> {
                                player.hurt(helper.getLevel().damageSources().mobAttack(husk), 3.0F);
                                expectReadoutEnd(helper, sink.lastRoll(), 7.5F, "HIT 1d4+1 = 5 → 7.5");
                            },
                            13,
                            4));
            helper.succeed();
        } finally {
            FeedbackSink.set(previous);
        }
    }

    /** A husk hits a full-health pig set up by {@code setup}: d20 13 + 3 vs AC 10, then 1d4+1 rolls {@code face}. */
    private static void hitReadout(
            GameTestHelper helper, Consumer<Pig> setup, int face, float expectedTaken, String expectedEnd) {
        CapturingFeedbackSink sink = new CapturingFeedbackSink();
        FeedbackSink previous = FeedbackSink.get();
        FeedbackSink.set(sink);
        try {
            Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
            Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
            setup.accept(pig);
            CombatScenarios.withRolls(
                    helper,
                    Rules.DEFAULTS,
                    () -> {
                        pig.hurt(helper.getLevel().damageSources().mobAttack(husk), 3.0F);
                        expectReadoutEnd(helper, sink.lastRoll(), expectedTaken, expectedEnd);
                    },
                    13,
                    face);
            helper.succeed();
        } finally {
            FeedbackSink.set(previous);
        }
    }

    private static void expectReadoutEnd(
            GameTestHelper helper, RollFeedbackPayload payload, float expectedTaken, String expectedEnd) {
        if (payload == null || payload.outcome() != AttackOutcome.HIT) {
            helper.fail("expected a HIT payload, was " + payload);
            return;
        }
        if (Math.abs(payload.taken() - expectedTaken) > 0.001F) {
            helper.fail("expected " + expectedTaken + " taken, was " + payload.taken());
        }
        String readout = CombatText.actionBar(payload).getString();
        if (!readout.endsWith(expectedEnd)) {
            helper.fail("expected the readout to end \"" + expectedEnd + "\", was \"" + readout + "\"");
        }
    }

    /** Pig health before each lethal roll: the 5-damage hit below would kill it outright. */
    private static final float LOW_HEALTH = 3.0F;

    public static void resistanceFiveLethalRollShowsNoKillLine(GameTestHelper helper) {
        lethalHit(
                helper,
                pig -> pig.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 4)),
                (pig, payload) -> {
                    expectSurvived(helper, pig, payload);
                    if (pig.getHealth() != LOW_HEALTH) {
                        helper.fail("Resistance V must negate the hit, health was " + pig.getHealth());
                    }
                    if (!hasResisted(payload)) {
                        helper.fail("a fully negated hit must be marked resisted, was " + payload);
                    }
                });
    }

    public static void totemLethalRollShowsNoKillLine(GameTestHelper helper) {
        lethalHit(
                helper,
                pig -> pig.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TOTEM_OF_UNDYING)),
                (pig, payload) -> {
                    expectSurvived(helper, pig, payload);
                    if (!pig.getMainHandItem().isEmpty()) {
                        helper.fail("the totem should have been used");
                    }
                    if (hasResisted(payload)) {
                        helper.fail("a totem-saved hit still took damage, must not read as resisted");
                    }
                });
    }

    /** At 1 HP a totem leaves health where it started; the hit was still lethal, not resisted. */
    public static void totemAtOneHealthIsNotMarkedResisted(GameTestHelper helper) {
        lethalHit(
                helper,
                1.0F,
                pig -> pig.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TOTEM_OF_UNDYING)),
                (pig, payload) -> {
                    expectSurvived(helper, pig, payload);
                    if (!pig.getMainHandItem().isEmpty()) {
                        helper.fail("the totem should have been used");
                    }
                    if (hasResisted(payload)) {
                        helper.fail("a totem-saved lethal hit must not read as resisted, was " + payload);
                    }
                });
    }

    public static void absorptionLethalRollShowsNoKillLine(GameTestHelper helper) {
        lethalHit(
                helper,
                pig -> {
                    pig.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 4));
                    if (pig.getAbsorptionAmount() < 8.0F) {
                        helper.fail("setup: Absorption V should give 20 absorption, was " + pig.getAbsorptionAmount());
                    }
                },
                (pig, payload) -> {
                    expectSurvived(helper, pig, payload);
                    if (pig.getHealth() != LOW_HEALTH || pig.getAbsorptionAmount() >= 20.0F) {
                        helper.fail("absorption should take the hit, health " + pig.getHealth() + " absorption "
                                + pig.getAbsorptionAmount());
                    }
                    if (hasResisted(payload)) {
                        helper.fail("an absorbed hit still took damage, must not read as resisted");
                    }
                });
    }

    public static void realKillShowsKillLine(GameTestHelper helper) {
        lethalHit(helper, pig -> {}, (pig, payload) -> {
            if (!pig.isDeadOrDying()) {
                helper.fail("the unprotected pig should have died");
            }
            if (payload.flavorKey().isEmpty() || !payload.flavorKey().get().contains(".kill.")) {
                helper.fail("a real kill must carry the kill line, was " + payload);
            }
            if (hasResisted(payload)) {
                helper.fail("a kill is not resisted");
            }
        });
    }

    /** A zoglin has no fists: its empty-handed kill reads from the natural-attack pool. */
    public static void zoglinKillShowsNaturalLine(GameTestHelper helper) {
        // d20 13 + 5 vs AC 10 hits, 1d6+2 rolls 6 for 8 damage
        emptyHandedKill(helper, EntityType.ZOGLIN, "critfall.flavor.natural.kill.", 13, 6);
    }

    /** A zombie fights with fists: its empty-handed kill keeps the unarmed line. */
    public static void zombieKillShowsUnarmedLine(GameTestHelper helper) {
        // d20 13 + 3 vs AC 10 hits, 1d4+1 rolls 4 for 5 damage
        emptyHandedKill(helper, EntityType.ZOMBIE, "critfall.flavor.default.kill.", 13, 4);
    }

    /**
     * An evoker fights with fists, but its fangs are a spell: an empty-handed spell kill reads from
     * the natural-attack pool. Vanilla fangs deal {@code minecraft:indirect_magic}, which ships in
     * {@code #critfall:exempt} and is never rolled, so this source keeps the fangs' shape (fangs
     * direct, evoker causing) with type {@code mob_attack} to reach the spell path, resolved here as
     * a saving throw.
     */
    public static void evokerFangKillShowsNaturalLine(GameTestHelper helper) {
        CapturingFeedbackSink sink = new CapturingFeedbackSink();
        FeedbackSink previous = FeedbackSink.get();
        FeedbackSink.set(sink);
        try {
            Evoker evoker = CombatScenarios.spawnCalm(helper, EntityType.EVOKER, 1, 1);
            Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
            pig.setHealth(LOW_HEALTH);
            if (!evoker.getType().is(CritfallTags.FIGHTS_WITH_FISTS)
                    || !evoker.getMainHandItem().isEmpty()) {
                helper.fail("setup: the evoker must be an empty-handed member of #critfall:fights_with_fists");
            }
            EvokerFangs fangs = new EvokerFangs(helper.getLevel(), pig.getX(), pig.getY(), pig.getZ(), 0.0F, 0, evoker);
            DamageSource bite = new DamageSource(
                    helper.getLevel()
                            .registryAccess()
                            .registryOrThrow(Registries.DAMAGE_TYPE)
                            .getHolderOrThrow(DamageTypes.MOB_ATTACK),
                    fangs,
                    evoker);
            SpellScenarios.withSpellProfile(
                    "{\"matches\": [\"minecraft:mob_attack\"], \"resolution\": \"save\", \"damage\": \"2d6\","
                            + " \"save\": {\"dc\": 13}}",
                    () -> FlavorScenarios.withFlavorPick(
                            false,
                            () -> CombatScenarios.withRolls(
                                    helper,
                                    CombatScenarios.EVERY_KILL_LINE,
                                    () -> {
                                        // pig save 2 + 0 vs DC 13 fails; 2d6 rolls 6, 6 for 12 damage
                                        pig.hurt(bite, 6.0F);
                                        SaveFeedbackPayload payload = sink.lastSave();
                                        if (!pig.isDeadOrDying()) {
                                            helper.fail("the pig should have died, payload " + payload);
                                        }
                                        if (payload == null
                                                || payload.flavorKey().isEmpty()
                                                || !payload.flavorKey()
                                                        .get()
                                                        .startsWith("critfall.flavor.natural.kill.")) {
                                            helper.fail(
                                                    "an evoker-fang kill must read as a natural kill, was " + payload);
                                        }
                                    },
                                    2,
                                    6,
                                    6)));
            helper.succeed();
        } finally {
            FeedbackSink.set(previous);
        }
    }

    /**
     * The save readout shows what was taken, like the attack readout: evoker fangs rolled as a save, the pig's
     * 2 + 0 fails DC 13, 2d6 rolls 3 and 2 for 5, and Resistance III leaves 2.
     */
    public static void saveReadoutShowsDamageTaken(GameTestHelper helper) {
        CapturingFeedbackSink sink = new CapturingFeedbackSink();
        FeedbackSink previous = FeedbackSink.get();
        FeedbackSink.set(sink);
        try {
            Evoker evoker = CombatScenarios.spawnCalm(helper, EntityType.EVOKER, 1, 1);
            Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
            pig.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 2));
            EvokerFangs fangs = new EvokerFangs(helper.getLevel(), pig.getX(), pig.getY(), pig.getZ(), 0.0F, 0, evoker);
            DamageSource bite = new DamageSource(
                    helper.getLevel()
                            .registryAccess()
                            .registryOrThrow(Registries.DAMAGE_TYPE)
                            .getHolderOrThrow(DamageTypes.MOB_ATTACK),
                    fangs,
                    evoker);
            SpellScenarios.withSpellProfile(
                    "{\"matches\": [\"minecraft:mob_attack\"], \"resolution\": \"save\", \"damage\": \"2d6\","
                            + " \"save\": {\"dc\": 13}}",
                    () -> CombatScenarios.withRolls(
                            helper,
                            Rules.DEFAULTS,
                            () -> {
                                pig.hurt(bite, 6.0F);
                                SaveFeedbackPayload payload = sink.lastSave();
                                String readout = payload == null
                                        ? "none"
                                        : CombatText.actionBar(payload).getString();
                                if (!readout.endsWith("FAILED 2d6 = 5 → 2")) {
                                    helper.fail("expected the save readout to end \"FAILED 2d6 = 5 → 2\", was \""
                                            + readout + "\"");
                                }
                            },
                            2,
                            3,
                            2));
            helper.succeed();
        } finally {
            FeedbackSink.set(previous);
        }
    }

    private static void emptyHandedKill(
            GameTestHelper helper, EntityType<? extends Mob> attackerType, String expectedPrefix, int... faces) {
        CapturingFeedbackSink sink = new CapturingFeedbackSink();
        FeedbackSink previous = FeedbackSink.get();
        FeedbackSink.set(sink);
        try {
            Mob attacker = CombatScenarios.spawnCalm(helper, attackerType, 1, 1);
            Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
            pig.setHealth(LOW_HEALTH);
            if (!attacker.getMainHandItem().isEmpty()) {
                helper.fail("setup: the attacker must be empty-handed, held " + attacker.getMainHandItem());
            }
            FlavorScenarios.withFlavorPick(
                    false,
                    () -> CombatScenarios.withRolls(
                            helper,
                            CombatScenarios.EVERY_KILL_LINE,
                            () -> {
                                pig.hurt(helper.getLevel().damageSources().mobAttack(attacker), 3.0F);
                                RollFeedbackPayload payload = sink.lastRoll();
                                if (!pig.isDeadOrDying()) {
                                    helper.fail("the pig should have died, payload " + payload);
                                }
                                if (payload == null
                                        || payload.flavorKey().isEmpty()
                                        || !payload.flavorKey().get().startsWith(expectedPrefix)) {
                                    helper.fail("expected a " + expectedPrefix + "* kill line, was " + payload);
                                }
                            },
                            faces));
            helper.succeed();
        } finally {
            FeedbackSink.set(previous);
        }
    }

    /**
     * A husk hits a {@code health} pig: d20 13 + 3 vs AC 10 hits, 1d4+1 rolls 4 for 5 damage
     * — lethal as rolled. {@code protect} sets up what may keep the pig alive.
     */
    private static void lethalHit(
            GameTestHelper helper, Consumer<Pig> protect, BiConsumer<Pig, RollFeedbackPayload> check) {
        lethalHit(helper, LOW_HEALTH, protect, check);
    }

    private static void lethalHit(
            GameTestHelper helper, float health, Consumer<Pig> protect, BiConsumer<Pig, RollFeedbackPayload> check) {
        CapturingFeedbackSink sink = new CapturingFeedbackSink();
        FeedbackSink previous = FeedbackSink.get();
        FeedbackSink.set(sink);
        try {
            Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
            Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
            pig.setHealth(health);
            protect.accept(pig);
            CombatScenarios.withRolls(
                    helper,
                    CombatScenarios.EVERY_KILL_LINE,
                    () -> {
                        pig.hurt(helper.getLevel().damageSources().mobAttack(husk), 3.0F);
                        RollFeedbackPayload payload = sink.lastRoll();
                        if (payload == null || payload.outcome() != AttackOutcome.HIT || payload.damage() != 5) {
                            helper.fail("expected a 5-damage HIT payload, was " + payload);
                        }
                        check.accept(pig, payload);
                    },
                    13,
                    4);
            helper.succeed();
        } finally {
            FeedbackSink.set(previous);
        }
    }

    private static void expectSurvived(GameTestHelper helper, Pig pig, RollFeedbackPayload payload) {
        if (pig.isDeadOrDying()) {
            helper.fail("the pig should have survived the hit");
        }
        if (payload.flavorKey().isPresent()) {
            helper.fail("a survived hit must not show a kill line, was "
                    + payload.flavorKey().get());
        }
    }

    private static boolean hasResisted(RollFeedbackPayload payload) {
        return payload.consequences().stream().anyMatch(c -> c.key().equals(ConsequenceLine.RESISTED));
    }
}
