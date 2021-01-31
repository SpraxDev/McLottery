package de.sprax2013.mc.lottery;

import de.sprax2013.lime.spigot.third_party.de.sprax2013.advanced_dev_utils.spigot.builder.ItemBuilder;
import de.sprax2013.lime.spigot.third_party.de.sprax2013.advanced_dev_utils.spigot.builder.inventory.InventoryBuilder;
import de.sprax2013.lime.utils.StringUtils;
import de.sprax2013.mc.lottery.files.Messages;
import de.sprax2013.mc.lottery.files.Settings;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// TODO: Remove duplicate code
// TODO: Put inventories into own file(s) to improve readability
@SuppressWarnings("deprecation")
// FIXME: The used deprecated methods are from my Lib. I know what I'm doing, it's only temporary
public class LotteryCommand implements CommandExecutor, TabCompleter {
    private final ItemStack invFiller = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).setDisplayName(" ").build();
    private final ItemStack invClose = new ItemBuilder(Material.BARRIER).setDisplayName("§4Beenden").build();

    private final String adminPerm;

    public LotteryCommand(JavaPlugin plugin) {
        this.adminPerm = plugin.getDescription().getName() + ".command.admin";
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                Player p = (Player) sender;

                if (LotteryPlugin.getManager().getLotteryCount() > 0) {
                    p.openInventory(
                            LotteryPlugin.getManager().getLotteryCount() > 1 ?
                                    getLotteryListInv(p) :
                                    getLotteryInv(p, LotteryPlugin.getManager().getLotteries().get(0), false));
                } else {
                    p.sendMessage("§4Aktuell sind keine Lotterien verfügbar");
                }
            } else {
                sender.sendMessage("§cDie Lotterie ist nur für Spieler verfügbar.");
            }
        } else {
            if (sender.hasPermission(this.adminPerm)) {
                String givenLotteryName = String.join(" ", Arrays.copyOfRange(args, 1, args.length) /* remove arg[0] */);

                if (args[0].equalsIgnoreCase("list")) {
                    List<Lottery> lotteries = LotteryPlugin.getManager().getLotteries();

                    if (!lotteries.isEmpty()) {
                        StringBuilder msg = new StringBuilder("§6Name §7(§eDateiname§7)§7: ");
                        boolean untouched = true;

                        for (Lottery l : lotteries) {
                            if (!untouched) {
                                msg.append("§7, ");
                            }

                            msg.append(ChatColor.GOLD)
                                    .append(l.getName())
                                    .append(" §7(§3§of:§e")
                                    .append(l.getFileName())
                                    .append("§7)");

                            untouched = false;
                        }

                        sender.sendMessage(msg.toString());
                    } else {
                        sender.sendMessage("§eEs existieren keine Lotterien.");
                    }
                } else if (args[0].equalsIgnoreCase("create")) {
                    if (sender instanceof Player) {
                        if (args.length > 1) {
                            try {
                                Lottery dummyLottery = Lottery.getDummyLottery();
                                dummyLottery.setName(ChatColor.translateAlternateColorCodes('&', givenLotteryName));

                                ((Player) sender).openInventory(getLotteryCreateOrEditInv((Player) sender, null, dummyLottery));
                            } catch (IOException ex) {
                                sender.sendMessage("§cEs ist ein Fehler aufgetreten§7: §r" +
                                        ex.getClass().getName() +
                                        (ex.getMessage() != null ? " (" + ex.getMessage() + ")" : "")
                                );

                                ex.printStackTrace();
                            }
                        } else {
                            sender.sendMessage("§3/" + cmd.getName() + " create §7<§eName§7>");
                        }
                    } else {
                        // TODO
                        sender.sendMessage("§cAktuell nur für Spieler verfügbar!");
                    }
                } else if (args[0].equalsIgnoreCase("delete")) {
                    if (args.length > 1) {
                        List<Lottery> searchResult = LotteryPlugin.getManager().searchLottery(givenLotteryName);

                        if (searchResult.isEmpty()) {
                            sender.sendMessage("§cDie Lotterie §e" + givenLotteryName + "§c wurde nicht gefunden.");
                        } else if (searchResult.size() > 1) {
                            StringBuilder msg = new StringBuilder("§cEs wurden mehrere Lotterien gefunden§7:");

                            for (Lottery lottery : searchResult) {
                                msg.append("\n§8- §e")
                                        .append(lottery.getName())
                                        .append(" §7<§6f:")
                                        .append(lottery.getFileName())
                                        .append("§7>");
                            }

                            sender.sendMessage(msg.toString());
                            sender.sendMessage("§eBitte nutze §3/" + cmd.getName() + " delete §e§of:§7<§eFileName§7>");
                        } else {
                            Lottery lottery = searchResult.get(0);

                            try {
                                LotteryPlugin.getManager().deleteLottery(lottery);

                                sender.sendMessage("§aLottery §e" + lottery.getName() + "§a wurde gelöscht.");
                            } catch (Exception ex) {
                                sender.sendMessage("§cEs ist ein Fehler beim Löschen aufgetreten§7: §r" +
                                        ex.getClass().getName() +
                                        (ex.getMessage() != null ? " (" + ex.getMessage() + ")" : "")
                                );

                                ex.printStackTrace();
                            }
                        }
                    } else {
                        sender.sendMessage("§3/" + cmd.getName() + " delete §7<§eName §7| §6§of:§eFilename§7>");
                    }
                } else if (args[0].equalsIgnoreCase("edit")) {
                    if (args.length > 1) {
                        List<Lottery> searchResult = LotteryPlugin.getManager().searchLottery(givenLotteryName);

                        if (searchResult.isEmpty()) {
                            sender.sendMessage("§cDie Lotterie §e" + givenLotteryName + "§c wurde nicht gefunden.");
                        } else if (searchResult.size() > 1) {
                            StringBuilder msg = new StringBuilder("§cEs wurden mehrere Lotterien gefunden§7:");

                            for (Lottery lottery : searchResult) {
                                msg.append("\n§8- §e")
                                        .append(lottery.getName())
                                        .append(" §7<§6f:")
                                        .append(lottery.getFileName())
                                        .append("§7>");
                            }

                            sender.sendMessage(msg.toString());
                            sender.sendMessage("§eBitte nutze §3/" + cmd.getName() + " edit §e§of:§7<§eFileName§7>");
                        } else {
                            Lottery lottery = searchResult.get(0);

                            try {
                                ((Player) sender).openInventory(getLotteryCreateOrEditInv((Player) sender, lottery, Lottery.getDummyLottery(lottery)));
                            } catch (Exception ex) {
                                sender.sendMessage("§cEs ist ein Fehler aufgetreten§7: §r" +
                                        ex.getClass().getName() +
                                        (ex.getMessage() != null ? " (" + ex.getMessage() + ")" : "")
                                );

                                ex.printStackTrace();
                            }
                        }
                    } else {
                        sender.sendMessage("§3/" + cmd.getName() + " edit §7<§eName §7| §6§of:§eFilename§7>");
                    }
                } else if (args[0].equalsIgnoreCase("reload") || args[0].equalsIgnoreCase("rl")) {
                    if (Messages.reload()) {
                        sender.sendMessage(Messages.getPrefix() + " §aSuccessfully reloaded §6messages.yml§a!");
                    } else {
                        sender.sendMessage(Messages.getPrefix() + " §cCould not reload §6messages.yml §7- §cCheck server logs for more information");
                    }

                    if (Settings.reload()) {
                        sender.sendMessage(Messages.getPrefix() + " §aSuccessfully reloaded §6config.yml§a!");
                    } else {
                        sender.sendMessage(Messages.getPrefix() + " §cCould not reload §6config.yml §7- §cCheck server logs for more information");
                    }
                } else {
                    sender.sendMessage("§3/" + cmd.getName() + "§7[§elist §7|§e create §7|§e delete §7|§e edit §7|§e reload]");
                }
            } else {
                sender.sendMessage(ChatColor.DARK_AQUA + cmd.getName());
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (sender.hasPermission(this.adminPerm)) {
            if (args.length == 1) {
                return StringUtils.getMatches(args[0], Arrays.asList("list", "create", "delete", "edit", "reload"), true);
            } else if (args.length == 2) {
                if (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("edit")) {
                    List<String> possible = new ArrayList<>(LotteryPlugin.getManager().getLotteryCount() * 2);

                    for (Lottery l : LotteryPlugin.getManager().getLotteries()) {
                        possible.add(l.getName());
                        possible.add("f:" + l.getFileName());
                    }

                    return StringUtils.getMatches(args[1], possible, true);
                }
            }
        }

        return Collections.emptyList();
    }

    @SuppressWarnings("deprecation")
    private Inventory getLotteryListInv(@NotNull Player p) {
        List<Lottery> lotteries = LotteryPlugin.getManager().getLotteries();

        int slots = (int) (Math.ceil(lotteries.size() / 9.0) * 9) + 9;

        InventoryBuilder invB = new InventoryBuilder(slots, "§6Wähle eine Lotterie");
        invB.setItems(0, slots - 1, invFiller);

        // TODO: align items centered
        int i = 0;
        for (Lottery lottery : lotteries) {
            invB.setItem(i, new ItemBuilder(Material.PAPER)
                            .addItemFlag(ItemFlag.HIDE_ENCHANTS)
                            .addEnchantment(Enchantment.DURABILITY, lottery.hasBoughtATicket(p.getUniqueId()) ? 1 : -1, true)

                            .setDisplayName(ChatColor.RESET + lottery.getName())
                            .addLore("§6Pot§7: §e" + lottery.getPot())
                            .addLore("")
                            .addLore("§6Preis§7: §e" + lottery.getPrice())
                            .build(),
                    e -> e.getEntity().openInventory(getLotteryInv(p, lottery, true)));

            i++;
        }

        invB.setItem(slots - 5, invClose, e -> e.getEntity().closeInventory());
        return invB.build();
    }

    @SuppressWarnings("deprecation")
    private Inventory getLotteryInv(@NotNull Player p, @NotNull Lottery lottery, boolean showReturnArrow) {
        InventoryBuilder invB = new InventoryBuilder(3 * 9, "§l§1" + lottery.getName());

        invB.setItems(0, 3 * 9 - 1, invFiller);

        invB.setItem(11, new ItemBuilder(Material.NAME_TAG)
                        .addItemFlag(ItemFlag.HIDE_ENCHANTS)
                        .setDisplayName("§2Ticket kaufen §7(§e$" + lottery.getPrice() + "§7)")
                        .addLore(lottery.hasBoughtATicket(p.getUniqueId()) ? "§4Du hast bereits ein Ticket gekauft" : null)
                        .addEnchantment(Enchantment.DURABILITY, lottery.hasBoughtATicket(p.getUniqueId()) ? 1 : -1, true)
                        .build(),
                e -> {
                    if (lottery.hasBoughtATicket(p.getUniqueId())) {
                        p.sendMessage("§cDu hast bereits ein Ticket für diese Lotterie gekauft.");
                    } else {
                        Economy eco = LotteryPlugin.getEconomy();

                        int balance = (int) eco.getBalance(p);

                        if (balance >= lottery.getPrice()) {
                            lottery.buyTicket(p.getUniqueId());

                            if (lottery.writeToFile()) {
                                p.openInventory(getLotteryInv(p, lottery, showReturnArrow));

                                EconomyResponse ecoRes = eco.withdrawPlayer(p, lottery.getPrice());

                                if (ecoRes.transactionSuccess()) {
                                    p.sendMessage("§aDu hast ein Lotterieticket für§e $" + lottery.getPrice() + "§a gekauft.");
                                } else {
                                    lottery.removeTicket(p.getUniqueId());

                                    if (!lottery.writeToFile()) {
                                        p.closeInventory();
                                        p.sendMessage("§cEs ist ein kritischer Fehler aufgetreten!");
                                    } else {
                                        p.sendMessage("§cWir konnten kein Geld von deime Konto abziehen - Bitte versuche es später noch einmal.");
                                    }
                                }
                            } else {
                                p.closeInventory();

                                p.sendMessage("§cEs ist ein kritischer Fehler aufgetreten!");
                            }
                        } else {
                            p.sendMessage("§cDir fehlen§e $" + (lottery.getPrice() - balance) +
                                    "§c, um dir dieses Ticket leisten zu können");
                        }
                    }
                });

        invB.setItem(15, new ItemBuilder(Material.DIAMOND).setDisplayName("§5Aktueller Pot§7: §e$" + lottery.getPot()).build(),
                e -> p.sendMessage("§aEs befinden sich§e $" + lottery.getPot() + "§a im Pot."));

        if (showReturnArrow) {
            invB.setItem(18,
                    new ItemBuilder(Material.ARROW).setDisplayName("Zurück zur Übersicht").build(),
                    e -> p.openInventory(getLotteryListInv(p)));
        }

        invB.setItem(22, invClose, e -> e.getEntity().closeInventory());

        return invB.build();
    }

    private Double getClickAmount(ClickType clickType) {
        switch (clickType) {
            case LEFT:
                return 1.0;
            case SHIFT_LEFT:
                return 10.0;
            case RIGHT:
                return 0.05;
            case SHIFT_RIGHT:
                return 0.5;
        }

        return null;
    }

    private Inventory getLotteryCreateOrEditInv(@NotNull Player p, @Nullable Lottery lottery, @NotNull Lottery dummyLottery) {
        InventoryBuilder invB = new InventoryBuilder(5 * 9, "§eErstelle Lotterie§7: §6" + dummyLottery.getName());

        ItemStack skullAdd = new ItemBuilder(Material.PLAYER_HEAD)
                .setDisplayName("§aErhöhen")
                .setSkullSkin("https://textures.minecraft.net/texture/3edd20be93520949e6ce789dc4f43efaeb28c717ee6bfcbbe02780142f716")

                .addLore("§3Linksklick§7: §a+1")
                .addLore("§3Sneak §7+§3 Linksklick§7: §a+10")
                .addLore("")
                .addLore("§3Rechtsklick§7: §a+0.05")
                .addLore("§3Sneak §7+§3 Rechtsklick§7: §a+0.5")
                .build();

        ItemStack skullSubtract = new ItemBuilder(Material.PLAYER_HEAD)
                .setDisplayName("§4Verringern")
                .setSkullSkin("https://textures.minecraft.net/texture/bd8a99db2c37ec71d7199cd52639981a7513ce9cca9626a3936f965b131193")

                .addLore("§3Linksklick§7: §4-1")
                .addLore("§3Sneak §7+§3 Linksklick§7: §4-10")
                .addLore("")
                .addLore("§3Rechtsklick§7: §4-0.05")
                .addLore("§3Sneak §7+§3 Rechtsklick§7: §4-0.5")
                .build();

        /* Price */

        invB.setItem(0, new ItemBuilder(skullAdd)
                        .addLore("§eAktuell§7: §6" + dummyLottery.getPrice(), 0)
                        .addLore("", 1)
                        .build(),
                e -> {
                    Double clickAmount = getClickAmount(e.getClickType());

                    if (clickAmount != null) {
                        dummyLottery.setPrice(round(dummyLottery.getPrice() + clickAmount));

                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, .75f, .75f);
                        p.openInventory(getLotteryCreateOrEditInv(p, lottery, dummyLottery));
                    }
                });
        invB.setItem(9, new ItemBuilder(Material.GOLD_NUGGET)
                        .setDisplayName("§eTicketpreis§7: §6" + dummyLottery.getPrice())
                        .addLore("§3Mausradklick§7: §4Auf 0 setzen")
                        .build(),
                e -> {
                    if (e.getClickType() == ClickType.MIDDLE) {
                        if (dummyLottery.getPrice() == 0) {
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1, .75f);
                        } else {
                            dummyLottery.setPrice(0);

                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, .75f, .75f);
                            p.openInventory(getLotteryCreateOrEditInv(p, lottery, dummyLottery));
                        }
                    }
                });
        invB.setItem(18, new ItemBuilder(skullSubtract)
                        .addLore("§eAktuell§7: §6" + dummyLottery.getPrice(), 0)
                        .addLore("", 1)
                        .build(),
                e -> {
                    Double clickAmount = getClickAmount(e.getClickType());

                    if (clickAmount != null) {
                        double currPrice = dummyLottery.getPrice();

                        if (currPrice == 0) {
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1, .75f);
                        } else {
                            double newPrice = round(currPrice - clickAmount);

                            if (newPrice < 0) {
                                newPrice = 0;
                            }

                            dummyLottery.setPrice(newPrice);

                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, .75f, .75f);
                            p.openInventory(getLotteryCreateOrEditInv(p, lottery, dummyLottery));
                        }
                    }
                });

        /* Starting pot */

        invB.setItem(2, new ItemBuilder(skullAdd)
                        .addLore("§eAktuell§7: §6" + dummyLottery.getStartingPot(), 0)
                        .addLore("", 1)
                        .build(),
                e -> {
                    Double clickAmount = getClickAmount(e.getClickType());

                    if (clickAmount != null) {
                        dummyLottery.setStartingPot(round(dummyLottery.getStartingPot() + clickAmount));

                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, .75f, .75f);
                        p.openInventory(getLotteryCreateOrEditInv(p, lottery, dummyLottery));
                    }
                });
        invB.setItem(11, new ItemBuilder(Material.DIAMOND)
                        .setDisplayName("§eAnfangspot§7: §6" + dummyLottery.getStartingPot())
                        .addLore("§3Mausradklick§7: §4Auf 0 setzen")
                        .build(),
                e -> {
                    if (e.getClickType() == ClickType.MIDDLE) {
                        if (dummyLottery.getStartingPot() == 0) {
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1, .75f);
                        } else {
                            dummyLottery.setStartingPot(0);

                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, .75f, .75f);
                            p.openInventory(getLotteryCreateOrEditInv(p, lottery, dummyLottery));
                        }
                    }
                });
        invB.setItem(20, new ItemBuilder(skullSubtract)
                        .addLore("§eAktuell§7: §e" + dummyLottery.getStartingPot(), 0)
                        .addLore("", 1)
                        .build(),
                e -> {
                    Double clickAmount = getClickAmount(e.getClickType());

                    if (clickAmount != null) {
                        double currStatingPot = dummyLottery.getStartingPot();

                        if (currStatingPot == 0) {
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1, .75f);
                        } else {
                            double newStartingPot = round(currStatingPot - clickAmount);

                            if (newStartingPot < 0) {
                                newStartingPot = 0;
                            }

                            dummyLottery.setStartingPot(newStartingPot);

                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, .75f, .75f);
                            p.openInventory(getLotteryCreateOrEditInv(p, lottery, dummyLottery));
                        }
                    }
                });

        /* IntervalType */

        invB.setItem(13, new ItemBuilder(Material.REDSTONE_BLOCK)
                        .setDisplayName("§eIntervall-Typ§7: §6" + dummyLottery.getIntervalType())
                        .addLore("§3Linksklick§7: §aNächste Option")
                        .addLore("§3Rechtsklick§7: §aVorherige Option")
                        .build(),
                e -> {
                    int currTypeIndex = 0;

                    for (int i = 0; i < IntervalType.values().length; ++i) {
                        if (IntervalType.values()[i].equals(dummyLottery.getIntervalType())) {
                            currTypeIndex = i;
                            break;
                        }
                    }

                    IntervalType type = null;

                    if (e.getClickType() == ClickType.LEFT) {
                        type = IntervalType.values()[++currTypeIndex >= IntervalType.values().length ? 0 : currTypeIndex];
                    } else if (e.getClickType() == ClickType.RIGHT) {
                        type = IntervalType.values()[--currTypeIndex < 0 ? IntervalType.values().length - 1 : currTypeIndex];
                    }

                    if (type != null) {
                        int relativeDay = dummyLottery.getIntervalRelativeDay();

                        if (type == IntervalType.DAILY ||
                                (type == IntervalType.WEEKLY && (relativeDay < 1 || relativeDay > 7)) ||
                                (type == IntervalType.MONTHLY && (relativeDay < 1 || relativeDay > 31))) {

                            dummyLottery.setIntervalRelativeDay(1);
                        }

                        dummyLottery.setIntervalType(type);

                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, .75f, .75f);
                        p.openInventory(getLotteryCreateOrEditInv(p, lottery, dummyLottery));
                    }
                });

        /* IntervalDay */

        String currIntervalDayFriendly = (dummyLottery.getIntervalType() == IntervalType.WEEKLY ? DayOfWeek.of(dummyLottery.getIntervalRelativeDay()).name() :
                (dummyLottery.getIntervalType() == IntervalType.MONTHLY ? dummyLottery.getIntervalRelativeDay() + ". Tag des Monats" : "-"));

        invB.setItem(6, new ItemBuilder(skullAdd)
                        .clearLore()
                        .addLore("§eAktuell§7: §6" + currIntervalDayFriendly)
                        .addLore("")
                        .addLore("§3Linksklick§7: §a+1")
                        .addLore("§3Sneak §7+§3 Linksklick§7: §a+5")
                        .build(),
                e -> {
                    int clickAmount = 0;
                    if (e.getClickType() == ClickType.LEFT) {
                        clickAmount = 1;
                    } else if (e.getClickType() == ClickType.SHIFT_LEFT) {
                        clickAmount = 5;
                    }

                    if (clickAmount != 0) {
                        int newRelativeDay = dummyLottery.getIntervalRelativeDay() + clickAmount;
                        boolean playSuccessSound = true;

                        if (dummyLottery.getIntervalType() == IntervalType.DAILY) {
                            newRelativeDay = 1;
                            playSuccessSound = false;

                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1, .75f);
                        } else if (dummyLottery.getIntervalType() == IntervalType.WEEKLY) {
                            if (newRelativeDay > 7) {
                                newRelativeDay = newRelativeDay - 7;
                            }
                        } else if (dummyLottery.getIntervalType() == IntervalType.MONTHLY) {
                            if (newRelativeDay > 31) {
                                newRelativeDay = newRelativeDay - 31;
                            }
                        }

                        dummyLottery.setIntervalRelativeDay(newRelativeDay);

                        if (playSuccessSound) {
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, .75f, .75f);
                        }

                        p.openInventory(getLotteryCreateOrEditInv(p, lottery, dummyLottery));
                    }
                });
        invB.setItem(15, new ItemBuilder(Material.BOOK)
                .setDisplayName("§eTag§7: §6" + currIntervalDayFriendly)
                .build());
        invB.setItem(24, new ItemBuilder(skullSubtract)
                        .clearLore()
                        .addLore("§eAktuell§7: §6" + currIntervalDayFriendly)
                        .addLore("")
                        .addLore("§3Linksklick§7: §4-1")
                        .addLore("§3Sneak §7+§3 Linksklick§7: §4-5")
                        .build(),
                e -> {
                    int clickAmount = 0;
                    if (e.getClickType() == ClickType.LEFT) {
                        clickAmount = 1;
                    } else if (e.getClickType() == ClickType.SHIFT_LEFT) {
                        clickAmount = 5;
                    }

                    if (clickAmount != 0) {
                        int newRelativeDay = dummyLottery.getIntervalRelativeDay() - clickAmount;
                        boolean playSuccessSound = true;

                        if (dummyLottery.getIntervalType() == IntervalType.DAILY) {
                            newRelativeDay = 1;
                            playSuccessSound = false;

                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1, .75f);
                        } else if (dummyLottery.getIntervalType() == IntervalType.WEEKLY) {
                            if (newRelativeDay < 1) {
                                newRelativeDay = newRelativeDay + 7;
                            }
                        } else if (dummyLottery.getIntervalType() == IntervalType.MONTHLY) {
                            if (newRelativeDay < 1) {
                                newRelativeDay = newRelativeDay + 31;
                            }
                        }

                        dummyLottery.setIntervalRelativeDay(newRelativeDay);

                        if (playSuccessSound) {
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, .75f, .75f);
                        }

                        p.openInventory(getLotteryCreateOrEditInv(p, lottery, dummyLottery));
                    }
                });

        /* Time of day */

        invB.setItem(8, new ItemBuilder(skullAdd)
                        .clearLore()
                        .addLore("§eAktuell§7: §6" + dummyLottery.getIntervalTime())
                        .addLore("")
                        .addLore("§3Linksklick§7: §a+1 Hour")
                        .addLore("§3Sneak §7+§3 Linksklick§7: §a+5 Hours")
                        .addLore("")
                        .addLore("§3Rechtsklick§7: §a+1 Minute")
                        .addLore("§3Sneak §7+§3 Rechtsklick§7: §a+5 Minutes")
                        .build(),
                e -> {
                    int clickAmount = 0;
                    boolean addToHour = false;

                    if (e.getClickType() == ClickType.LEFT) {
                        clickAmount = 1;
                        addToHour = true;
                    } else if (e.getClickType() == ClickType.SHIFT_LEFT) {
                        addToHour = true;
                        clickAmount = 5;
                    } else if (e.getClickType() == ClickType.RIGHT) {
                        clickAmount = 1;
                    } else if (e.getClickType() == ClickType.SHIFT_RIGHT) {
                        clickAmount = 5;
                    }

                    if (clickAmount != 0) {
                        LocalTime time = LocalTime.parse(dummyLottery.getIntervalTime());

                        if (addToHour) {
                            time = time.plusHours(clickAmount);
                        } else {
                            time = time.plusMinutes(clickAmount);
                        }

                        dummyLottery.setIntervalTime(time.toString());

                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, .75f, .75f);
                        p.openInventory(getLotteryCreateOrEditInv(p, lottery, dummyLottery));
                    }
                });
        invB.setItem(17, new ItemBuilder(Material.CLOCK)
                .setDisplayName("§eUhrzeit§7: §6" + dummyLottery.getIntervalTime())
                .addLore("§3Mausradklick§7: §aAuf die aktuelle Uhrzeit setzen")
                .build(), e -> {
            if (e.getClickType() == ClickType.MIDDLE) {
                dummyLottery.setIntervalTime(LocalTime.now().withSecond(0).withNano(0).toString());

                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, .75f, .75f);
                p.openInventory(getLotteryCreateOrEditInv(p, lottery, dummyLottery));
            }
        });
        invB.setItem(26, new ItemBuilder(skullSubtract)
                        .clearLore()
                        .addLore("§eAktuell§7: §6" + dummyLottery.getIntervalTime())
                        .addLore("")
                        .addLore("§3Linksklick§7: §4-1 Hour")
                        .addLore("§3Sneak §7+§3 Linksklick§7: §4-5 Hours")
                        .addLore("")
                        .addLore("§3Rechtsklick§7: §4-1 Minute")
                        .addLore("§3Sneak §7+§3 Rechtsklick§7: §4-5 Minutes")
                        .build(),
                e -> {
                    int clickAmount = 0;
                    boolean addToHour = false;

                    if (e.getClickType() == ClickType.LEFT) {
                        clickAmount = 1;
                        addToHour = true;
                    } else if (e.getClickType() == ClickType.SHIFT_LEFT) {
                        addToHour = true;
                        clickAmount = 5;
                    } else if (e.getClickType() == ClickType.RIGHT) {
                        clickAmount = 1;
                    } else if (e.getClickType() == ClickType.SHIFT_RIGHT) {
                        clickAmount = 5;
                    }

                    if (clickAmount != 0) {
                        LocalTime time = LocalTime.parse(dummyLottery.getIntervalTime());

                        if (addToHour) {
                            time = time.minusHours(clickAmount);
                        } else {
                            time = time.minusMinutes(clickAmount);
                        }

                        dummyLottery.setIntervalTime(time.toString());

                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, .75f, .75f);
                        p.openInventory(getLotteryCreateOrEditInv(p, lottery, dummyLottery));
                    }
                });

        /* Cancel */

        invB.setItem(39, new ItemBuilder(Material.BARRIER)
                        .setDisplayName("§4Abbrechen")
                        .build(),
                e -> e.getEntity().closeInventory());

        /* Submit */

        invB.setItem(41, new ItemBuilder(Material.PLAYER_HEAD)
                .setSkullSkin("http://textures.minecraft.net/texture/4a1eb43be9976d4ef0bd2871b7014fe79ecdaec3d2d333619da8d0a6c90a682c")

                .setDisplayName(lottery == null ? "§aLotterie erstellen" : "§aÄnderungen speichern")

                // TODO: This looks fucking awful
                .addLore("§eName§7: §r" + ((lottery == null || lottery.getName().equals(dummyLottery.getName())) ? "§f" + dummyLottery.getName() : ("§f§m" + lottery.getName() + "§3 ->§f " + dummyLottery.getName())))
                .addLore("")
                .addLore("§ePreis§7: §r" + ((lottery == null || lottery.getPrice() == dummyLottery.getPrice()) ? "§f" + dummyLottery.getPrice() : ("§f§m" + lottery.getPrice() + "§3 ->§f " + dummyLottery.getPrice())))
                .addLore("§eAnfangspot§7: §r" + ((lottery == null || lottery.getStartingPot() == dummyLottery.getStartingPot()) ? "§f" + dummyLottery.getStartingPot() : ("§f§m" + lottery.getStartingPot() + "§3 ->§f " + dummyLottery.getStartingPot())))
                .addLore("")
                .addLore("§eIntervall-Typ§7: §r" + ((lottery == null || lottery.getIntervalType().equals(dummyLottery.getIntervalType())) ? "§f" + dummyLottery.getIntervalType() : ("§f§m" + lottery.getIntervalType() + "§3 ->§f " + dummyLottery.getIntervalType())))
                .addLore("§eIntervall-Tag§7: §r" + ((lottery == null || lottery.getIntervalRelativeDay() == dummyLottery.getIntervalRelativeDay()) ? "§f" + dummyLottery.getIntervalRelativeDay() : ("§f§m" + lottery.getIntervalRelativeDay() + "§3 ->§f " + dummyLottery.getIntervalRelativeDay())))
                .addLore("§eIntervall-Zeit§7: §r" + ((lottery == null || lottery.getIntervalTime().equals(dummyLottery.getIntervalTime())) ? "§f" + dummyLottery.getIntervalTime() : ("§f§m" + lottery.getIntervalTime() + "§3 ->§f " + dummyLottery.getIntervalTime())))

                .build(), e -> {
            // Create new lottery
            if (lottery == null) {
                try {
                    LotteryPlugin.getManager().createLottery(dummyLottery.getName(),
                            dummyLottery.getPrice(), dummyLottery.getStartingPot(),
                            dummyLottery.getIntervalType(), dummyLottery.getIntervalTime(), dummyLottery.getIntervalRelativeDay());

                    p.sendMessage("§aLottery §e" + dummyLottery.getName() + "§a wurde erstellt.");
                } catch (IOException ex) {
                    ex.printStackTrace();

                    // TODO: Send error to user
                }
            } else {    // Edit existing one
                lottery.setName(dummyLottery.getName());

                lottery.setPrice(dummyLottery.getPrice());
                lottery.setStartingPot(dummyLottery.getStartingPot());

                lottery.setIntervalType(dummyLottery.getIntervalType());
                lottery.setIntervalTime(dummyLottery.getIntervalTime());
                lottery.setIntervalRelativeDay(dummyLottery.getIntervalRelativeDay());

                if (lottery.writeToFile()) {
                    p.sendMessage("§aÄnderungen an §e" + lottery.getName() + "§a wurden gespeichert.");
                } else {
                    p.sendMessage("§cEs ist ein Fehler beim Speichern aufgetreten!");
                    // TODO: Send Exception#getMessage() etc. to the player
                }
            }

            p.closeInventory();
        });

        return invB.build();
    }

    private double round(double value) {
        return new BigDecimal(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}