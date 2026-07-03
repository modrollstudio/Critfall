package studio.modroll.critfall;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loader-agnostic entrypoint. Each loader module calls {@link #init()} once at mod construction. */
public final class Critfall {

    public static final String MOD_ID = "critfall";
    public static final String MOD_NAME = "Critfall";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    private Critfall() {}

    public static void init() {
        LOG.info("{} initialized", MOD_NAME);
    }
}
