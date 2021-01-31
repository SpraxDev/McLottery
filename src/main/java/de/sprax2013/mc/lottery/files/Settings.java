package de.sprax2013.mc.lottery.files;

import de.sprax2013.lime.configuration.Config;
import de.sprax2013.lime.configuration.ConfigEntry;
import de.sprax2013.mc.lottery.LotteryPlugin;

import java.io.File;

public class Settings {
    protected static final String HEADER = "Lottery by Sprax\n\n" +
            "Support: https://Sprax.me/Discord\n" +
            "Updates and Information:\n" +
            "Statistics: https://bstats.org/plugin/bukkit/Lottery_/10192";

    private static final Config config = new Config(
            new File(LotteryPlugin.getPlugin(LotteryPlugin.class).getDataFolder(), "config.yml"), HEADER);

    public static final ConfigEntry UPDATER_ENABLED = config.createEntry(
            "Updater.CheckForUpdates", true,
            "Should we check for new versions and report to the console? (Recommended)");
    public static final ConfigEntry UPDATER_NOTIFY_ON_JOIN = config.createEntry(
            "Updater.NotifyOnJoin", true,
            () -> "Should be notify admins when they join the server? (Permission: " +
                    LotteryPlugin.getPlugin(LotteryPlugin.class).getName() + ".updater)");

    private Settings() {
        throw new IllegalStateException("Utility class");
    }

    public static Config getConfig() {
        return config;
    }

    public static boolean reload() {
        return config.load() && config.save();
    }

    public static void reset() {
        ConfigHelper.reset(config);
    }
}