package studio.modroll.critfall.feedback;

import java.util.Optional;
import studio.modroll.critfall.combat.Rules;

/**
 * One player-facing announcement of a fired outcome consequence (PLAN §4.5: "a triggered outcome
 * states which consequence fired"). {@code key} is a translation key resolved client-side (or shown
 * literally in the modless fallback); {@code arg} is an optional already-resolved name (a redirect
 * victim, an applied status effect). Flat strings only — this crosses the network in the feedback
 * payload and must round-trip without registry access.
 */
public record ConsequenceLine(String key, Optional<String> arg) {

    public static final String DURABILITY_BROKEN = "critfall.consequence.durability.broken";
    public static final String DURABILITY_WORN = "critfall.consequence.durability.worn";
    public static final String HIT_ALLY = "critfall.consequence.hit_ally";
    public static final String SELF_DAMAGE = "critfall.consequence.self_damage";
    public static final String DROP_WEAPON = "critfall.consequence.drop_weapon";
    public static final String STUMBLE = "critfall.consequence.stumble";
    public static final String APPLY_EFFECT = "critfall.consequence.apply_effect";
    public static final String KNOCKBACK = "critfall.consequence.knockback";

    public static ConsequenceLine of(String key) {
        return new ConsequenceLine(key, Optional.empty());
    }

    public static ConsequenceLine of(String key, String arg) {
        return new ConsequenceLine(key, Optional.of(arg));
    }

    public static ConsequenceLine durability(Rules.DurabilityMode mode) {
        return of(mode == Rules.DurabilityMode.SET_TO_1 ? DURABILITY_BROKEN : DURABILITY_WORN);
    }
}
