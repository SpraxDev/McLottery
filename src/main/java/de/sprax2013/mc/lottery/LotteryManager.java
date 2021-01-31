package de.sprax2013.mc.lottery;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class LotteryManager {
    final File lotteryDir;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private final List<Lottery> lotteries = new ArrayList<>(0);

    LotteryManager() {
        this.lotteryDir = new File(JavaPlugin.getPlugin(LotteryPlugin.class).getDataFolder(), "lotteries");

        if (this.lotteryDir.exists()) {
            for (File f : this.lotteryDir.listFiles()) {
                if (f.getName().toLowerCase().endsWith(".yml")) {
                    try {
                        Lottery lottery = new Lottery(f);
                        this.lotteries.add(lottery);

                        initLottery(lottery);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }

        LotteryPlugin.getPluginLogger().info(getLotteryCount() + (getLotteryCount() == 1 ? " lottery" : " lotteries")
                + " have been initialized.");
    }

    public void stopLotteries() {
        scheduler.shutdown();
    }

    public int getLotteryCount() {
        return lotteries.size();
    }

    public List<Lottery> getLotteries() {
        return Collections.unmodifiableList(this.lotteries);
    }

    public void createLottery(@NotNull String name, double price, double startingPot,
                              @NotNull IntervalType intervalType, @NotNull String intervalTime, int relativeIntervalDay) throws IOException {
        Lottery lottery = new Lottery(name.trim(), price, startingPot, intervalType, intervalTime, relativeIntervalDay);

        lottery.writeToFile();
        lotteries.add(lottery);

        initLottery(lottery);
    }

    public void deleteLottery(@NotNull Lottery lottery) throws IOException {
        lottery.delete();

        lotteries.remove(lottery);
    }

    public List<Lottery> searchLottery(@NotNull String query) {
        List<Lottery> result = new ArrayList<>();

        boolean byFileName = query.toLowerCase(Locale.ROOT).startsWith("f:");
        String nameToSearch = byFileName ? query.substring(2) : query;

        for (Lottery l : this.lotteries) {
            if (byFileName) {
                if (l.getFileName().equals(nameToSearch)) {
                    result.add(l);

                    break;
                }
            } else if (ChatColor.stripColor(l.getName()).equalsIgnoreCase(nameToSearch)) {
                result.add(l);
            }
        }

        return result;
    }

    public void initLottery(@NotNull Lottery lottery) {
        if (lottery.isDeleted()) throw new IllegalArgumentException("The lottery has already been deleted");
        if (lottery.isDummy()) throw new IllegalArgumentException("Cannot perform this action on a dummy lottery");

        LocalTime time = LocalTime.parse(lottery.getIntervalTime());

        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime nextRun = now.withHour(time.getHour())
                .withMinute(time.getMinute())
                .withSecond(time.getSecond());

        switch (lottery.getIntervalType()) {
            case DAILY:
                // Ignore the relative day, as daily already matches every day
                break;
            case WEEKLY:
                nextRun = nextRun.with(TemporalAdjusters.next(DayOfWeek.of(lottery.getIntervalRelativeDay())));
                break;
            case MONTHLY:
                nextRun = nextRun.withDayOfMonth(lottery.getIntervalRelativeDay());
                break;

            default:
                throw new IllegalStateException("Unknown IntervalType for Lottery");
        }

        if (now.compareTo(nextRun) > 0) {
            switch (lottery.getIntervalType()) {
                case DAILY:
                    nextRun = nextRun.plusDays(1);
                    break;
                case WEEKLY:
                    nextRun = nextRun.plusWeeks(1);
                    break;
                case MONTHLY:
                    nextRun = nextRun.plusMonths(1);
                    break;

                default:
                    throw new IllegalStateException("Unknown IntervalType for Lottery");
            }
        }

        Duration duration = Duration.between(now, nextRun);
        long initialDelay = duration.getSeconds();

        AtomicReference<ScheduledFuture<?>> taskReference = new AtomicReference<>();
        taskReference.set(scheduler.scheduleAtFixedRate(() -> {
            if (lottery.isDeleted()) {
                if (taskReference.get() != null) {
                    taskReference.get().cancel(true);
                }

                return;
            }

            double pot = lottery.getPot();
            UUID winner = lottery.getRandomParticipant();

            if (winner != null) {
                OfflinePlayer p = Bukkit.getOfflinePlayer(winner);

                boolean vaultSuccess = true;
                String error = null;

                if (!LotteryPlugin.getEconomy().depositPlayer(p, pot).transactionSuccess()) {
                    vaultSuccess = false;
                    error = "Vault could not transfer money ($" + pot + ") to " + p.getName() + " (" + p.getUniqueId() + ")";
                } else if (!lottery.reset()) {
                    error = "Could not reset lottery";
                }

                if (error != null) {
                    if (vaultSuccess &&
                            !LotteryPlugin.getEconomy().withdrawPlayer(p, pot).transactionSuccess()) {
                        LotteryPlugin.getPluginLogger()
                                .warning("Die Lotterie wird aufgrund eines Fehler abgebrochen, doch der Spieler "
                                        + p.getName() + " (" + p.getUniqueId() + ") hat bereits seine Auszahlung erhalten!");
                    }

                    LotteryPlugin.getPluginLogger()
                            .warning("Es ist ein kritischer Fehler beim Auflösen der Lotterie aufgetreten! Die Gewinnverkündung von " +
                                    p.getName() + " (" + p.getUniqueId() + ") in Höhe von $" + pot + " wurde abgebrochen: " + error);

                    Bukkit.broadcastMessage("§cDie heutige Lotterieziehung wurde aufgrund eines Systemfehlers verschoben.");
                } else {
                    Bukkit.broadcastMessage("§aDie heutige Lotterie hat§e " + p.getName() + "§a mit einem Pot von§e " + pot + "§a gewonnen!");
                }
            } else {
                Bukkit.broadcastMessage("§aAn der heutigen Lotterie hat keiner Teilgenommen - Sie wurde ohne Gewinner beendet.");
            }
        }, initialDelay, TimeUnit.DAYS.toSeconds(1), TimeUnit.SECONDS));
    }
}