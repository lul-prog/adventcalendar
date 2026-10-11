package com.adventcalendar.data;

import com.adventcalendar.util.JsonIO;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Content of {@code rewards.json}: the reward assigned to every day plus the debug section
 * (date override used by the debug GUI / commands).
 *
 * <p>The file is keyed by day number so it stays readable and hand editable:</p>
 * <pre>
 * {
 *   "schema": 1,
 *   "days": { "1": { "item": { "id": "minecraft:cookie", "count": 3 } } },
 *   "debug": { "date_override": "2026-12-01" }
 * }
 * </pre>
 */
public final class RewardsFile {
    public static final int SCHEMA = 1;

    private final Map<Integer, Reward> days = new TreeMap<>();
    private String dateOverride = "";

    public Map<Integer, Reward> entries() {
        return days;
    }

    public Reward get(int day) {
        return days.get(day);
    }

    public void set(int day, Reward reward) {
        if (reward == null || reward.isEmpty()) {
            days.remove(day);
        } else {
            days.put(day, reward);
        }
    }

    public void clear(int day) {
        days.remove(day);
    }

    public void clearAll() {
        days.clear();
    }

    public int countConfigured() {
        return days.size();
    }

    /** Bitmask of the configured days (bit {@code day - 1}), used by the GUI packets. */
    public int configuredMask() {
        int mask = 0;
        for (Integer day : days.keySet()) {
            if (day != null && day >= 1 && day <= 31) {
                mask |= 1 << (day - 1);
            }
        }
        return mask;
    }

    public String dateOverride() {
        return dateOverride;
    }

    public void setDateOverride(String value) {
        dateOverride = value == null ? "" : value;
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA);
        root.addProperty("_comment",
                "Advent Calendar rewards. Keys are day numbers of December. "
                        + "Each day takes either an \"item\" (id/count/nbt), a \"command\", or both.");
        JsonObject daysJson = new JsonObject();
        for (Map.Entry<Integer, Reward> entry : days.entrySet()) {
            daysJson.add(String.valueOf(entry.getKey()), entry.getValue().toJson());
        }
        root.add("days", daysJson);

        JsonObject debug = new JsonObject();
        if (!dateOverride.isEmpty()) {
            debug.addProperty("date_override", dateOverride);
        } else {
            debug.add("date_override", null);
        }
        root.add("debug", debug);
        return root;
    }

    public static RewardsFile fromJson(JsonObject root) {
        RewardsFile file = new RewardsFile();
        if (root == null) {
            return file;
        }
        JsonObject daysJson = root.has("days") && root.get("days").isJsonObject()
                ? root.getAsJsonObject("days")
                : null;
        if (daysJson != null) {
            for (Map.Entry<String, JsonElement> entry : daysJson.entrySet()) {
                int day;
                try {
                    day = Integer.parseInt(entry.getKey());
                } catch (NumberFormatException e) {
                    continue;
                }
                if (day < 1 || day > 31) {
                    continue;
                }
                Reward reward = Reward.fromJson(entry.getValue());
                if (reward != null) {
                    file.days.put(day, reward);
                }
            }
        }
        if (root.has("debug") && root.get("debug").isJsonObject()) {
            JsonObject debug = root.getAsJsonObject("debug");
            file.dateOverride = JsonIO.string(debug, "date_override", "");
            if (!file.dateOverride.isEmpty()) {
                try {
                    LocalDate.parse(file.dateOverride);
                } catch (RuntimeException e) {
                    file.dateOverride = "";
                }
            }
        }
        return file;
    }

    /** Convenience for the "fill sample rewards" button and tests. */
    public Map<Integer, Reward> snapshot() {
        return new LinkedHashMap<>(days);
    }
}
