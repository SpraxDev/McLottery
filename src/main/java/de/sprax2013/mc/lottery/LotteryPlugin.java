package de.sprax2013.mc.lottery;

import de.sprax2013.lime.spigot.LimeDevUtilitySpigot;
import de.sprax2013.mc.lottery.files.Messages;
import de.sprax2013.mc.lottery.files.Settings;
import net.milkbowl.vault.economy.Economy;
import org.bstats.bukkit.MetricsLite;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Logger;

public class LotteryPlugin extends JavaPlugin {
    public Economy vaultEconomy;
    public LotteryManager manager;

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().warning("Vault (Economy) could be setup - The plugin is disabling itself.");
            Bukkit.getPluginManager().disablePlugin(this);

            return;
        }

        LimeDevUtilitySpigot.init(this);

        // Init configuration files
        Settings.reload();
        Messages.reload();

        this.manager = new LotteryManager();

        Objects.requireNonNull(getCommand("lottery")).setExecutor(new LotteryCommand(this));

        new MetricsLite(this, 10192);
        new Updater(this);
    }

    @Override
    public void onDisable() {
        if (this.manager != null) {
            this.manager.stopLotteries();
        }

        Settings.reset();
        Messages.reset();
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().isPluginEnabled("Vault")) {
            RegisteredServiceProvider<Economy> serviceProvider = getServer().getServicesManager().getRegistration(Economy.class);

            if (serviceProvider != null) {
                vaultEconomy = serviceProvider.getProvider();
            }
        }

        return vaultEconomy != null;
    }

    public static LotteryManager getManager() {
        return getPlugin(LotteryPlugin.class).manager;
    }

    public static Economy getEconomy() {
        return getPlugin(LotteryPlugin.class).vaultEconomy;
    }

    public static Logger getPluginLogger() {
        return getPlugin(LotteryPlugin.class).getLogger();
    }
}