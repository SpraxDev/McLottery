package de.sprax2013.mc.lottery.files;

import de.sprax2013.lime.configuration.Config;
import de.sprax2013.lime.configuration.ConfigEntry;
import de.sprax2013.lime.configuration.validation.StringEntryValidator;
import de.sprax2013.mc.lottery.LotteryPlugin;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;

// TODO: Extract all the strings into this class
public class Messages {
    public static final String ERR_ASYNC_API_CALL = "Async API call";
    public static final String ERR_ANOTHER_PLUGIN_PREVENTING_SPAWN = "Looks like another plugin is preventing BetterChairs from spawning chairs";
    public static final String ERR_NOT_CUSTOM_ARMOR_STAND = "The provided ArmorStand is not an instance of '%s'";

    private static final Config config = new Config(
            new File(JavaPlugin.getPlugin(LotteryPlugin.class).getDataFolder(), "messages.yml"), Settings.HEADER)
            .withCommentEntry("ToggleChairs", "What should we tell players when they enable or disable chairs for themselves");

    private static final ConfigEntry PREFIX = config.createEntry(
            "General.Prefix", "&7[&2" + JavaPlugin.getPlugin(LotteryPlugin.class).getName() + "&7]",
            "The prefix that can be used in all other messages")
            .setEntryValidator(StringEntryValidator.get());
//    public static final ConfigEntry NO_PERMISSION = config.createEntry(
//            "General.NoPermission", "${Prefix} &cYou do not have permission to use this command!",
//            "What should we tell players that are not allowed to use an command?")
//            .setEntryValidator(StringEntryValidator.get());

    private Messages() {
        throw new IllegalStateException("Utility class");
    }

    public static Config getConfig() {
        return config;
    }

    public static String getPrefix() {
        return ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(PREFIX.getValueAsString()));
    }

    public static String getString(ConfigEntry cfgEntry) {
        return ChatColor.translateAlternateColorCodes('&',
                Objects.requireNonNull(cfgEntry.getValueAsString()))
                .replace("${Prefix}", getPrefix());
    }

    public static boolean reload() {
        return config.load() && config.save();
    }

    public static void reset() {
        ConfigHelper.reset(config);
    }
}