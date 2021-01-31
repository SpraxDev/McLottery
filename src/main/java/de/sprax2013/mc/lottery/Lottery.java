package de.sprax2013.mc.lottery;

import de.sprax2013.lime.configuration.Config;
import de.sprax2013.lime.configuration.ConfigEntry;
import de.sprax2013.lime.configuration.validation.DoubleEntryValidator;
import de.sprax2013.lime.configuration.validation.EnumEntryValidator;
import de.sprax2013.lime.configuration.validation.IntEntryValidator;
import de.sprax2013.lime.configuration.validation.StringEntryValidator;
import de.sprax2013.lime.utils.StringUtils;
import org.bukkit.ChatColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class Lottery {
    private final Config cfg;

    private final ConfigEntry cfgName;
    private final ConfigEntry cfgPrice;
    private final ConfigEntry cfgStartingPot;

    private final ConfigEntry cfgParticipants;

    private final ConfigEntry cfgIntervalType;
    private final ConfigEntry cfgIntervalTime;
    private final ConfigEntry cfgIntervalRelativeDay;

    private boolean dummy;
    private boolean deleted;

    Lottery(@NotNull File file) throws IOException {
        this.cfg = new Config(file, "If you edit this file and break something, it's on you.");

        this.cfgName = cfg.createEntry("Name", "Lottery")
                .setEntryValidator(StringEntryValidator.get());
        this.cfgPrice = cfg.createEntry("Price", 20)
                .setEntryValidator(DoubleEntryValidator.get(IntEntryValidator.MathSign.POSITIVE));
        this.cfgStartingPot = cfg.createEntry("StartingPot", 50)
                .setEntryValidator(DoubleEntryValidator.get(IntEntryValidator.MathSign.POSITIVE));
        this.cfgParticipants = cfg.createEntry("Participants", new ArrayList<>());

        this.cfgIntervalType = cfg.createEntry("Interval.Type", IntervalType.DAILY)
                .setEntryValidator(EnumEntryValidator.get(IntervalType.class));
        this.cfgIntervalTime = cfg.createEntry("Interval.Time", "12:00");
        this.cfgIntervalRelativeDay = cfg.createEntry("Interval.RelativeDay", 1)
                .setEntryValidator(IntEntryValidator.get(IntEntryValidator.MathSign.POSITIVE));

        this.cfg.loadWithException();
    }

    Lottery(@NotNull String name, double price, double startingPot,
            @NotNull IntervalType intervalType, @NotNull String intervalTime, int relativeIntervalDay) throws IOException {
        this(getUnusedFile(name));

        this.cfgName.setValue(Objects.requireNonNull(name));
        this.cfgPrice.setValue(price);
        this.cfgStartingPot.setValue(startingPot);

        this.cfgIntervalType.setValue(Objects.requireNonNull(intervalType));
        this.cfgIntervalTime.setValue(Objects.requireNonNull(intervalTime));
        this.cfgIntervalRelativeDay.setValue(relativeIntervalDay);
    }

    @NotNull
    public String getName() {
        return Objects.requireNonNull(this.cfgName.getValueAsString());
    }

    public void setName(@NotNull String name) {
        this.cfgName.setValue(name);
    }

    @NotNull
    public String getFileName() {
        return Objects.requireNonNull(this.cfg.getFile()).getName();
    }

    public double getStartingPot() {
        return this.cfgStartingPot.getValueAsDouble();
    }

    public void setStartingPot(double startingPot) {
        if (startingPot < 0) throw new IllegalArgumentException("The lottery starting pot cannot be negative");

        this.cfgStartingPot.setValue(startingPot);
    }

    public double getPot() {
        return getStartingPot() + (getPrice() * getParticipantCount());
    }

    public double getPrice() {
        return this.cfgPrice.getValueAsDouble();
    }

    public void setPrice(double price) {
        if (price < 0) throw new IllegalArgumentException("The lottery price cannot be negative");

        this.cfgPrice.setValue(price);
    }

    public int getParticipantCount() {
        return Objects.requireNonNull(this.cfgParticipants.getValueAsList()).size();
    }

    @SuppressWarnings("unchecked")
    public List<UUID> getParticipants() {
        return Collections.unmodifiableList(Objects.requireNonNull((List<UUID>) this.cfgParticipants.getValue()));
    }

    @Nullable
    public UUID getRandomParticipant() {
        List<UUID> participants = getParticipants();

        if (participants.size() > 0) {
            return participants.get(ThreadLocalRandom.current().nextInt(participants.size()));
        }

        return null;
    }

    public IntervalType getIntervalType() {
        return this.cfgIntervalType.getValueAsEnum(IntervalType.class);
    }

    public void setIntervalType(IntervalType type) {
        this.cfgIntervalType.setValue(type);
    }

    public String getIntervalTime() {
        return this.cfgIntervalTime.getValueAsString();
    }

    public void setIntervalTime(@NotNull String time) {
        this.cfgIntervalTime.setValue(time);
    }

    public int getIntervalRelativeDay() {
        return this.cfgIntervalRelativeDay.getValueAsInt();
    }

    public void setIntervalRelativeDay(int relativeDay) {
        this.cfgIntervalRelativeDay.setValue(relativeDay);
    }

    public void buyTicket(UUID uuid) {
        if (this.deleted) throw new IllegalStateException("The lottery has already been deleted");
        if (this.dummy) throw new IllegalStateException("Cannot perform this action on a dummy lottery");
        if (hasBoughtATicket(uuid)) throw new IllegalArgumentException("That player already has a ticket!");

        Objects.requireNonNull(this.cfgParticipants.getValueAsList()).add(uuid);
    }

    public void removeTicket(UUID uuid) {
        if (this.deleted) throw new IllegalStateException("The lottery has already been deleted");
        if (this.dummy) throw new IllegalStateException("Cannot perform this action on a dummy lottery");

        if (!Objects.requireNonNull(this.cfgParticipants.getValueAsList()).remove(uuid)) {
            throw new IllegalArgumentException("That player doesn't have a ticket!");
        }
    }

    public boolean hasBoughtATicket(UUID uuid) {
        return Objects.requireNonNull(this.cfgParticipants.getValueAsList()).contains(uuid);
    }

    public boolean isDeleted() {
        return this.deleted;
    }

    public boolean isDummy() {
        return this.dummy;
    }

    public boolean reset() {
        if (this.deleted) throw new IllegalStateException("The lottery has already been deleted");
        if (this.dummy) throw new IllegalStateException("Cannot perform this action on a dummy lottery");

        this.cfgParticipants.setValue(new ArrayList<>());

        return writeToFile();
    }

    public boolean writeToFile() {
        if (this.deleted) throw new IllegalStateException("The lottery has already been deleted");
        if (this.dummy) throw new IllegalStateException("Cannot perform this action on a dummy lottery");

        try {
            this.cfg.saveWithException();

            return true;
        } catch (IOException ex) {
            LotteryPlugin.getPluginLogger()
                    .warning("Could not write lottery to file: " +
                            ex.getClass().getName() + (ex.getMessage() != null ? " (" + ex.getMessage() + ")" : ""));
        }

        return false;
    }

    void delete() throws IOException {
        if (this.deleted) throw new IllegalStateException("The lottery has already been deleted");
        if (this.dummy) throw new IllegalStateException("Cannot perform this action on a dummy lottery");

        this.deleted = true;

        Files.deleteIfExists(Objects.requireNonNull(this.cfg.getFile()).toPath());
        this.cfg.setFile(null);
    }

    public static Lottery getDummyLottery() throws IOException {
        return getDummyLottery(null);
    }

    public static Lottery getDummyLottery(@Nullable Lottery lotteryToCopyValuesFrom) throws IOException {
        Lottery result = new Lottery(new File(LotteryPlugin.getManager().lotteryDir, "dummy.tmp"));
        result.dummy = true;

        if (lotteryToCopyValuesFrom != null) {
            result.setName(lotteryToCopyValuesFrom.getName());

            result.setPrice(lotteryToCopyValuesFrom.getPrice());
            result.setStartingPot(lotteryToCopyValuesFrom.getStartingPot());

            result.setIntervalType(lotteryToCopyValuesFrom.getIntervalType());
            result.setIntervalTime(lotteryToCopyValuesFrom.getIntervalTime());
            result.setIntervalRelativeDay(lotteryToCopyValuesFrom.getIntervalRelativeDay());
        }

        return result;
    }

    private static File getUnusedFile(@NotNull String name) {
        name = ChatColor.stripColor(name);

        if (!name.matches("[a-zA-Z0-9_ ]+")) {
            name = Base64.getEncoder().withoutPadding().encodeToString(name.getBytes());
        }

        name = StringUtils.trimStringToMaxLength(name, 64);

        String resultingName = name;
        int i = 1;
        while (new File(LotteryPlugin.getManager().lotteryDir, resultingName + ".yml").exists()) {
            resultingName = name + "_" + i;

            i++;
        }

        return new File(LotteryPlugin.getManager().lotteryDir, resultingName + ".yml");
    }
}