package com.adventcalendar.client;

import com.adventcalendar.network.CalendarSyncPacket;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Client side mirror of the calendar: server date, unlock progress, claimed mask and the
 * reward icons the server decided to reveal. Every GUI reads from here, so the mod never has
 * to trust (or even consult) the local clock.
 */
public final class ClientCalendarState {
    private static final ClientCalendarState INSTANCE = new ClientCalendarState();

    private final Map<Integer, CalendarSyncPacket.DayEntry> entries = new HashMap<>();
    private final Map<Integer, Long> animations = new HashMap<>();

    private int days = 24;
    private int year;
    private int month;
    private int dayOfMonth;
    private int unlockedThrough;
    private int claimedMask;
    private int configuredMask;
    private int pendingCount;
    private boolean admin;
    private boolean dateOverridden;
    private boolean decemberOnly = true;
    private boolean preview;
    private boolean hideLocked = true;
    /** Debug only: makes the GUI look as if every door had been claimed already. */
    private boolean simulateClaimed;

    private ClientCalendarState() {
    }

    public static ClientCalendarState get() {
        return INSTANCE;
    }

    public void apply(CalendarSyncPacket packet) {
        apply(packet, true);
    }

    /** @param replaceEntries false keeps the local reward icons (used by the feedback packet) */
    public void apply(CalendarSyncPacket packet, boolean replaceEntries) {
        this.days = Math.max(1, packet.days);
        this.year = packet.year;
        this.month = packet.month;
        this.dayOfMonth = packet.dayOfMonth;
        this.unlockedThrough = packet.unlockedThrough;
        this.claimedMask = packet.claimedMask;
        this.configuredMask = packet.configuredMask;
        this.pendingCount = packet.pendingCount;
        this.admin = packet.admin;
        this.dateOverridden = packet.dateOverridden;
        this.decemberOnly = packet.decemberOnly;
        this.preview = packet.preview;
        this.hideLocked = packet.hideLocked;
        if (replaceEntries) {
            entries.clear();
            for (CalendarSyncPacket.DayEntry entry : packet.entries) {
                entries.put(entry.day, entry);
            }
        }
    }

    public void updateCounters(int claimedMask, int pendingCount) {
        this.claimedMask = claimedMask;
        this.pendingCount = pendingCount;
    }

    public void reset() {
        entries.clear();
        animations.clear();
        simulateClaimed = false;
    }

    public int days() {
        return days;
    }

    public int year() {
        return year;
    }

    public int month() {
        return month;
    }

    public int dayOfMonth() {
        return dayOfMonth;
    }

    public int unlockedThrough() {
        return unlockedThrough;
    }

    public int pendingCount() {
        return pendingCount;
    }

    public boolean isAdmin() {
        return admin;
    }

    public boolean isDateOverridden() {
        return dateOverridden;
    }

    public boolean isDecemberOnly() {
        return decemberOnly;
    }

    public boolean isPreview() {
        return preview;
    }

    public boolean isSimulating() {
        return simulateClaimed;
    }

    public void setSimulating(boolean value) {
        this.simulateClaimed = value;
    }

    public boolean isDecemberNow() {
        return month == 12;
    }

    public boolean isUnlocked(int day) {
        return day >= 1 && day <= days && day <= unlockedThrough;
    }

    public boolean isToday(int day) {
        return unlockedThrough >= 1 && day == Math.min(days, unlockedThrough);
    }

    public boolean isClaimed(int day) {
        if (simulateClaimed) {
            return true;
        }
        return day >= 1 && day <= 31 && (claimedMask & (1 << (day - 1))) != 0;
    }

    public boolean isConfigured(int day) {
        return day >= 1 && day <= 31 && (configuredMask & (1 << (day - 1))) != 0;
    }

    public DayState stateOf(int day) {
        if (isClaimed(day)) {
            return DayState.CLAIMED;
        }
        if (!isUnlocked(day) || !isConfigured(day)) {
            return DayState.LOCKED;
        }
        return DayState.AVAILABLE;
    }

    public ItemStack stackFor(int day) {
        CalendarSyncPacket.DayEntry entry = entries.get(day);
        return entry == null ? ItemStack.EMPTY : entry.stack;
    }

    @Nullable
    public String labelFor(int day) {
        CalendarSyncPacket.DayEntry entry = entries.get(day);
        return entry == null || entry.label.isEmpty() ? null : entry.label;
    }

    @Nullable
    public String commandFor(int day) {
        CalendarSyncPacket.DayEntry entry = entries.get(day);
        return entry == null || entry.command.isEmpty() ? null : entry.command;
    }

    /** True when the door is a command reward without an item of its own. */
    public boolean isCommandOnly(int day) {
        CalendarSyncPacket.DayEntry entry = entries.get(day);
        return entry != null && entry.commandOnly;
    }

    /** Stack used to paint the door: a command block for pure command rewards. */
    public ItemStack displayStackFor(int day) {
        ItemStack stack = stackFor(day);
        if (stack.isEmpty() && isCommandOnly(day)) {
            return new ItemStack(net.minecraft.world.item.Items.COMMAND_BLOCK);
        }
        return stack;
    }

    public boolean isHidden(int day) {
        CalendarSyncPacket.DayEntry entry = entries.get(day);
        return entry != null && entry.hidden;
    }

    /** ISO representation of the server date, used in the header and the tooltips. */
    public String dateString() {
        return String.format("%04d-%02d-%02d", year, month, dayOfMonth);
    }

    // ------------------------------------------------------------------ animations

    public void startClaimAnimation(int day) {
        animations.put(day, System.currentTimeMillis());
    }

    /** 0..1 while the celebration animation of a day plays, negative when there is none. */
    public float animationProgress(int day) {
        Long start = animations.get(day);
        if (start == null) {
            return -1.0F;
        }
        long elapsed = System.currentTimeMillis() - start;
        float duration = 900.0F;
        if (elapsed > duration) {
            animations.remove(day);
            return -1.0F;
        }
        return elapsed / duration;
    }

    public boolean hasEntries() {
        return !entries.isEmpty();
    }
}
