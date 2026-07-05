package studio.modroll.critfall.api.event;

import net.minecraft.world.entity.LivingEntity;
import studio.modroll.critfall.api.AttackContext;
import studio.modroll.critfall.combat.AttackResult;

/**
 * Fired after the attack resolved but before damage is applied (PLAN §4.4). Listeners see the full
 * {@link AttackResult} and may change the {@code finalDamage} that will be applied, or veto the
 * attack (resolved, but no damage and no outcome tables). {@code finalDamage} starts at
 * {@code result.damage()}.
 */
public final class PostAttackRollEvent {

    private final LivingEntity attacker;
    private final LivingEntity target;
    private final AttackContext context;
    private final AttackResult result;
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

    public void veto() {
        this.vetoed = true;
    }

    public boolean isVetoed() {
        return vetoed;
    }
}
