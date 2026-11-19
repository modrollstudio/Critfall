package studio.modroll.critfall.api;

import net.minecraft.world.entity.LivingEntity;

/**
 * Adds to a defender's Armor Class, such as a spell or a blessing. Any number of mods can register one with {@link
 * RollService#addArmorClassSource}, beside the one {@link ModifierProvider}. Since 0.2.20.
 */
@FunctionalInterface
public interface ArmorClassSource {

    /**
     * What this source adds to {@code defender}'s AC: {@code 0} for nothing, negative to lower it. Asked on every
     * attack roll, real time and driven, on every {@link RollService#performHazardAttack hazard attack}, and by
     * {@link RollService#armorClass}. A throw or a value beyond ±1,000,000 counts as {@code 0} and is logged once.
     *
     * @param defender the entity being attacked
     * @param attacker the attacker; null for a hazard attack, and for {@link RollService#armorClass} asked without one
     * @param armorClassSoFar the AC before this source: Critfall's own, the {@link ModifierProvider}'s AC modifier,
     *     and what the sources registered earlier added. A floor of 17 returns {@code Math.max(0, 17 -
     *     armorClassSoFar)}
     */
    int add(LivingEntity defender, LivingEntity attacker, int armorClassSoFar);
}
