package studio.modroll.critfall.api.event;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import studio.modroll.critfall.api.HazardAttack;
import studio.modroll.critfall.api.HazardSave;
import studio.modroll.critfall.api.dice.RollMode;

/**
 * Fired before the d20 of a {@link studio.modroll.critfall.api.RollService#performHazardSave hazard save}
 * or {@link studio.modroll.critfall.api.RollService#performHazardAttack hazard attack}. Listeners {@link
 * #grantAdvantage grant advantage} or {@link #imposeDisadvantage impose disadvantage}, for example advantage
 * on saves against poison, and Critfall combines them the 5e way into {@link #mode}. Since 0.2.14 they can
 * also {@link #addBonus add a bonus} and {@link #rerollOnes reroll a natural 1}. Hazards have no attacker,
 * so they fire this instead of {@link PreAttackRollEvent}. Since 0.2.13.
 */
public final class HazardRollEvent {

    /** Whether the target saves, or the hazard attacks. */
    public enum Kind {
        SAVE,
        ATTACK
    }

    private final LivingEntity target;
    private final HazardSave save;
    private final HazardAttack attack;
    private boolean advantage;
    private boolean disadvantage;
    private int bonus;
    private boolean rerollOnes;

    /** A hazard save; its own {@link HazardSave#mode()} counts as one source. */
    public HazardRollEvent(LivingEntity target, HazardSave save) {
        this.target = target;
        this.save = Objects.requireNonNull(save, "save");
        this.attack = null;
        countSource(save.mode());
    }

    /** A hazard attack; its own {@link HazardAttack#mode()} counts as one source. */
    public HazardRollEvent(LivingEntity target, HazardAttack attack) {
        this.target = target;
        this.save = null;
        this.attack = Objects.requireNonNull(attack, "attack");
        countSource(attack.mode());
    }

    private void countSource(RollMode mode) {
        if (mode == RollMode.ADVANTAGE) {
            grantAdvantage();
        } else if (mode == RollMode.DISADVANTAGE) {
            imposeDisadvantage();
        }
    }

    /** The entity saving, or being attacked. */
    public LivingEntity target() {
        return target;
    }

    public Kind kind() {
        return save != null ? Kind.SAVE : Kind.ATTACK;
    }

    /** Present for a hazard save, with its save key and DC. */
    public Optional<HazardSave> save() {
        return Optional.ofNullable(save);
    }

    /** Present for a hazard attack, with its attack bonus. */
    public Optional<HazardAttack> attack() {
        return Optional.ofNullable(attack);
    }

    public ResourceKey<DamageType> damageType() {
        return save != null ? save.damageType() : attack.damageType();
    }

    public String label() {
        return save != null ? save.label() : attack.label();
    }

    /** The hazard's {@link HazardSave#id() id} or {@link HazardAttack#id() id}, if it has one. Since 0.2.14. */
    public Optional<ResourceLocation> id() {
        return save != null ? save.id() : attack.id();
    }

    /** Adds a source of advantage. Several count as one. */
    public void grantAdvantage() {
        advantage = true;
    }

    /** Adds a source of disadvantage. Several count as one. */
    public void imposeDisadvantage() {
        disadvantage = true;
    }

    /**
     * The mode the d20 will roll with, from every source so far (the hazard's own and earlier listeners'):
     * advantage alone rolls with advantage, disadvantage alone with disadvantage, and both, or neither, roll
     * normally.
     */
    public RollMode mode() {
        if (advantage == disadvantage) {
            return RollMode.NORMAL;
        }
        return advantage ? RollMode.ADVANTAGE : RollMode.DISADVANTAGE;
    }

    /**
     * Adds {@code amount} to the save or attack total, on top of the save bonus or the hazard's attack bonus.
     * Bonuses from several listeners add up; a negative amount is a penalty. The readout's modifier includes
     * it. Since 0.2.14.
     */
    public void addBonus(int amount) {
        bonus += amount;
    }

    /** The sum of every {@link #addBonus} so far. Since 0.2.14. */
    public int bonus() {
        return bonus;
    }

    /**
     * A natural 1 on the d20 is rolled again, once, and the new roll stands even if it is another 1 (the
     * halfling's Luck). With advantage or disadvantage it happens when the kept die is a 1, and only that die
     * rolls again. Several listeners asking still reroll once. Since 0.2.14.
     */
    public void rerollOnes() {
        rerollOnes = true;
    }

    /** Whether a listener called {@link #rerollOnes}. Since 0.2.14. */
    public boolean rerollsOnes() {
        return rerollOnes;
    }
}
