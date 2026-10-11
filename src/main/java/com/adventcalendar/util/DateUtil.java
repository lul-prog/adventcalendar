package com.adventcalendar.util;

import com.adventcalendar.AdventConfig;

import javax.annotation.Nullable;
import java.time.LocalDate;

/**
 * Real-world clock of the server plus the optional debug override.
 *
 * <p>The whole mod works off the <b>real calendar date</b> of the machine running the
 * server: day {@code N} of the Advent Calendar unlocks when the real date reaches
 * December {@code N}. When {@link AdventConfig#DECEMBER_ONLY} is disabled the calendar
 * behaves like a monthly calendar instead (day {@code N} unlocks on the {@code N}-th of
 * any month), which is handy for testing outside December.</p>
 *
 * <p>Every value in here is derived, never stored: the debug override (admin GUI / debug
 * GUI / {@code /advent date set}) simply replaces "now" with another date so that all
 * date checks in the mod flip together.</p>
 */
public final class DateUtil {
    @Nullable
    private static LocalDate override;

    private DateUtil() {
    }

    /** Current date used for every date check in the mod (override aware). */
    public static LocalDate now() {
        return override != null ? override : LocalDate.now();
    }

    public static boolean hasOverride() {
        return override != null;
    }

    @Nullable
    public static LocalDate getOverride() {
        return override;
    }

    /** Raw setter, used when loading the persisted debug section. */
    public static void setOverride(@Nullable LocalDate date) {
        override = date;
    }

    public static void clearOverride() {
        override = null;
    }

    /** Calendar year the data is keyed by (so a new year starts with fresh claims). */
    public static int year() {
        return now().getYear();
    }

    /** True when the real (or overridden) month is December. */
    public static boolean isDecember() {
        return now().getMonthValue() == 12;
    }

    /** Number of doors of the calendar, clamped to a sane range. */
    public static int daysInCalendar() {
        int configured = AdventConfig.DAYS.get();
        return Math.max(1, Math.min(31, configured));
    }

    /**
     * Highest day number that is currently unlocked, {@code 0} when nothing is unlocked yet.
     */
    public static int unlockedThrough() {
        LocalDate date = now();
        if (AdventConfig.DECEMBER_ONLY.get()) {
            return date.getMonthValue() == 12 ? date.getDayOfMonth() : 0;
        }
        return date.getDayOfMonth();
    }

    /** True when the door of the given day may be opened today. */
    public static boolean isUnlocked(int day) {
        return day >= 1 && day <= daysInCalendar() && day <= unlockedThrough();
    }

    /** True when the given day is the most recently unlocked one ("today's" door). */
    public static boolean isToday(int day) {
        int unlocked = unlockedThrough();
        return unlocked >= 1 && day == Math.min(daysInCalendar(), unlocked);
    }

    /** ISO representation, e.g. {@code 2026-12-01}. */
    public static String format(LocalDate date) {
        return date.toString();
    }

    public static String format() {
        return format(now());
    }

    /**
     * Lenient parser used by commands and the debug GUI. Accepts
     * {@code YYYY-MM-DD}, {@code MM-DD} (current year) and {@code DD} (current month/year).
     *
     * @return the parsed date or {@code null} when the text cannot be understood
     */
    @Nullable
    public static LocalDate parse(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            String[] parts = value.split("-");
            LocalDate today = now();
            if (parts.length == 3) {
                return LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            }
            if (parts.length == 2) {
                return LocalDate.of(today.getYear(), Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            }
            if (parts.length == 1) {
                int day = Integer.parseInt(parts[0]);
                if (day < 1 || day > 31) {
                    return null;
                }
                // A bare day number means "that day of December, this year" when December is
                // already around, otherwise of the current month.
                int month = today.getMonthValue() >= 11 || today.getMonthValue() == 12 ? 12 : today.getMonthValue();
                LocalDate candidate = LocalDate.of(today.getYear(), month, 1).withDayOfMonth(
                        Math.min(day, LocalDate.of(today.getYear(), month, 1).lengthOfMonth()));
                return candidate;
            }
        } catch (NumberFormatException | java.time.DateTimeException ignored) {
            // fall through and report failure
        }
        return null;
    }
}
