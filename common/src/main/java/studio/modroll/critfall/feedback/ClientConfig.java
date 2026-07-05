package studio.modroll.critfall.feedback;

/**
 * Client-only feedback toggles (M6), loaded from {@code config/critfall/client.json}. Each layer is
 * independently switchable — kitchen-sink packs hate HUD spam. The server never reads this; it only
 * governs how the client renders a received feedback payload.
 */
public record ClientConfig(boolean rolls, boolean flavor, boolean sounds, boolean particles) {

    public static final int FORMAT_VERSION = 1;
    public static final ClientConfig DEFAULTS = new ClientConfig(true, true, true, true);
}
