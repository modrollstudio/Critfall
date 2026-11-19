package studio.modroll.critfall.api.event;

import net.minecraft.world.entity.LivingEntity;
import studio.modroll.critfall.RollRuntime;
import studio.modroll.critfall.api.AttackContext;
import studio.modroll.critfall.api.combat.AttackOutcome;
import studio.modroll.critfall.api.combat.AttackResult;

/**
 * Fired after the attack resolved but before damage is applied. Listeners see the full
 * {@link AttackResult} and may change the {@code finalDamage} that will be applied, raise the AC the roll
 * is measured against, or veto the attack (resolved, but no damage and no outcome tables).
 * {@code finalDamage} starts at {@code result.damage()}.
 */
public final class PostAttackRollEvent {

    private final LivingEntity attacker;
    private final LivingEntity target;
    private final AttackContext context;
    private AttackResult result;
    private int finalDamage;
    private boolean vetoed;

    public PostAttackRollEvent(LivingEntity attacker, LivingEntity target, AttackContext context, AttackResult result) {
        this.attacker = attacker;
        this.target = target;
        this.context = context;
        this.result = result;
        this.finalDamage = result.damage();
    }

    public LivingEntity attacker() {
        return attacker;
    }

    public LivingEntity target() {
        return target;
    }

    public AttackContext context() {
        return context;
    }

    public AttackResult result() {
        return result;
    }

    public int finalDamage() {
        return finalDamage;
    }

    public void finalDamage(int value) {
        this.finalDamage = value;
    }

    /**
     * Raises the AC the roll already made is measured against by {@code amount}, as a reaction after the roll
     * (Shield). A hit the new AC is above becomes a {@link AttackOutcome#MISS} with no damage, and {@link
     * #finalDamage()} drops to 0; a natural 20 still hits while {@code crits.nat20_always_hits} is on. {@link
     * #result()} shows the change at once, to this listener and the ones after it: the raise counts in its {@code
     * armorClass()} and {@code defenderAcBonus()}, so the readout shows {@code vs AC 19 (14+5) — MISS}. Calls add
     * up. Since 0.2.20.
     *
     * @throws IllegalArgumentException when {@code amount} is negative
     */
    public void raiseArmorClass(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must not be negative: " + amount);
        }
        int armorClass = result.armorClass() + amount;
        boolean natural20Hits =
                result.natural() == 20 && RollRuntime.rules().crits().nat20AlwaysHits();
        boolean nowMisses = result.isHit() && !natural20Hits && result.attackTotal() < armorClass;
        result = new AttackResult(
                nowMisses ? AttackOutcome.MISS : result.outcome(),
                result.natural(),
                result.attackTotal(),
                armorClass,
                result.defenderAcBonus() + amount,
                nowMisses ? 0 : result.damage(),
                result.roll());
        if (nowMisses) {
            finalDamage = 0;
        }
    }

    public void veto() {
        this.vetoed = true;
    }

    public boolean isVetoed() {
        return vetoed;
    }
}
