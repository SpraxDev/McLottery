package de.sprax2013.mc.lottery.files;

import de.sprax2013.lime.configuration.Config;

public class ConfigHelper {
    private ConfigHelper() {
        throw new IllegalStateException("Utility class");
    }

    static void reset(Config cfg) {
        cfg.clearListeners();
        cfg.reset();
    }
}