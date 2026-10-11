package com.adventcalendar.data;

import com.adventcalendar.util.ItemStackJson;
import com.adventcalendar.util.JsonIO;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Content of {@code claims.json}: which player (keyed by <b>UUID</b>, so it works the same
 * on LAN and on a dedicated server) claimed which day, plus the "stocking" of players that
 * use {@link com.adventcalendar.AdventConfig.GrantMode#STOCKING}.
 *
 * <pre>
 * {
 *   "schema": 1,
 *   "players": {
 *     "069a79f4-44e9-4726-a5be-fca90e38aaf5": {
 *       "name": "Notch",
 *       "years": { "2026": { "3": "2026-12-03T18:22:41Z" } },
 *       "last_notified": { "2026": 5 },
 *       "stocking": [ { "id": "minecraft:cake", "count": 1 } ]
 *     }
 *   }
 * }
 * </pre>
 */
public final class ClaimsFile {
    public static final int SCHEMA = 1;

    /** Per player data. */
    public static final class PlayerEntry {
        public String name = "";
        /** year -> (day -> ISO timestamp) */
        public final Map<Integer, Map<Integer, String>> years = new TreeMap<>();
        /** year -> last day we told the player about */
        public final Map<Integer, Integer> lastNotified = new TreeMap<>();
        /** gifts waiting to be picked up */
        public final List<ItemStack> stocking = new ArrayList<>();

        public Map<Integer, String> year(int year) {
            return years.computeIfAbsent(year, y -> new TreeMap<>());
        }

        public int mask(int year) {
            int mask = 0;
            for (Integer day : year(year).keySet()) {
                if (day != null && day >= 1 && day <= 31) {
                    mask |= 1 << (day - 1);
                }
            }
            return mask;
        }
    }

    private final Map<UUID, PlayerEntry> players = new HashMap<>();

    private PlayerEntry entry(UUID uuid) {
        return players.computeIfAbsent(uuid, id -> new PlayerEntry());
    }

    public void touch(UUID uuid, String name) {
        PlayerEntry entry = entry(uuid);
        if (name != null && !name.isEmpty() && !name.equals(entry.name)) {
            entry.name = name;
        }
    }

    public boolean isClaimed(UUID uuid, int year, int day) {
        PlayerEntry entry = players.get(uuid);
        return entry != null && entry.year(year).containsKey(day);
    }

    public String claimedAt(UUID uuid, int year, int day) {
        PlayerEntry entry = players.get(uuid);
        return entry == null ? null : entry.year(year).get(day);
    }

    public int claimedMask(UUID uuid, int year) {
        PlayerEntry entry = players.get(uuid);
        return entry == null ? 0 : entry.mask(year);
    }

    public Map<Integer, String> claimedDays(UUID uuid, int year) {
        PlayerEntry entry = players.get(uuid);
        return entry == null ? Map.of() : new TreeMap<>(entry.year(year));
    }

    public void markClaimed(UUID uuid, String name, int year, int day) {
        touch(uuid, name);
        entry(uuid).year(year).put(day, Instant.now().toString());
    }

    public int lastNotified(UUID uuid, int year) {
        PlayerEntry entry = players.get(uuid);
        if (entry == null) {
            return 0;
        }
        Integer value = entry.lastNotified.get(year);
        return value == null ? 0 : value;
    }

    public void setLastNotified(UUID uuid, int year, int day) {
        entry(uuid).lastNotified.put(year, day);
    }

    /** Removes every claim of one player (all years). Returns how many days were removed. */
    public int resetPlayer(UUID uuid) {
        PlayerEntry entry = players.get(uuid);
        if (entry == null) {
            return 0;
        }
        int removed = entry.years.values().stream().mapToInt(Map::size).sum();
        entry.years.clear();
        entry.stocking.clear();
        return removed;
    }

    /** Removes the claims of every player. Returns how many days were removed. */
    public int resetAll() {
        int removed = 0;
        for (PlayerEntry entry : players.values()) {
            removed += entry.years.values().stream().mapToInt(Map::size).sum();
            entry.years.clear();
        }
        return removed;
    }

    public void addToStocking(UUID uuid, String name, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        touch(uuid, name);
        entry(uuid).stocking.add(stack.copy());
    }

    public int stockingCount(UUID uuid) {
        PlayerEntry entry = players.get(uuid);
        return entry == null ? 0 : entry.stocking.size();
    }

    /** Removes and returns every gift waiting in the stocking. */
    public List<ItemStack> takeStocking(UUID uuid) {
        PlayerEntry entry = players.get(uuid);
        if (entry == null || entry.stocking.isEmpty()) {
            return List.of();
        }
        List<ItemStack> result = new ArrayList<>(entry.stocking);
        entry.stocking.clear();
        return result;
    }

    public int playerCount() {
        return players.size();
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA);
        root.addProperty("_comment",
                "Advent Calendar claims, keyed by player UUID (works on LAN and dedicated servers). "
                        + "Deleting a player entry resets their calendar.");
        JsonObject playersJson = new JsonObject();
        for (Map.Entry<UUID, PlayerEntry> entry : players.entrySet()) {
            PlayerEntry data = entry.getValue();
            JsonObject playerJson = new JsonObject();
            playerJson.addProperty("name", data.name);

            JsonObject years = new JsonObject();
            for (Map.Entry<Integer, Map<Integer, String>> yearEntry : data.years.entrySet()) {
                if (yearEntry.getValue().isEmpty()) {
                    continue;
                }
                JsonObject days = new JsonObject();
                for (Map.Entry<Integer, String> dayEntry : yearEntry.getValue().entrySet()) {
                    days.addProperty(String.valueOf(dayEntry.getKey()), dayEntry.getValue());
                }
                years.add(String.valueOf(yearEntry.getKey()), days);
            }
            playerJson.add("years", years);

            if (!data.lastNotified.isEmpty()) {
                JsonObject notified = new JsonObject();
                for (Map.Entry<Integer, Integer> notifiedEntry : data.lastNotified.entrySet()) {
                    notified.addProperty(String.valueOf(notifiedEntry.getKey()), notifiedEntry.getValue());
                }
                playerJson.add("last_notified", notified);
            }

            if (!data.stocking.isEmpty()) {
                JsonArray stocking = new JsonArray();
                for (ItemStack stack : data.stocking) {
                    JsonObject item = ItemStackJson.toJson(stack);
                    if (item != null) {
                        stocking.add(item);
                    }
                }
                playerJson.add("stocking", stocking);
            }
            playersJson.add(entry.getKey().toString(), playerJson);
        }
        root.add("players", playersJson);
        return root;
    }

    public static ClaimsFile fromJson(JsonObject root) {
        ClaimsFile file = new ClaimsFile();
        if (root == null || !root.has("players") || !root.get("players").isJsonObject()) {
            return file;
        }
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("players").entrySet()) {
            UUID uuid = parseUuid(entry.getKey());
            if (uuid == null || !entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject json = entry.getValue().getAsJsonObject();
            PlayerEntry data = new PlayerEntry();
            data.name = JsonIO.string(json, "name", "");

            if (json.has("years") && json.get("years").isJsonObject()) {
                for (Map.Entry<String, JsonElement> yearEntry : json.getAsJsonObject("years").entrySet()) {
                    Integer year = parseInt(yearEntry.getKey());
                    if (year == null || !yearEntry.getValue().isJsonObject()) {
                        continue;
                    }
                    Map<Integer, String> days = new TreeMap<>();
                    for (Map.Entry<String, JsonElement> dayEntry : yearEntry.getValue().getAsJsonObject().entrySet()) {
                        Integer day = parseInt(dayEntry.getKey());
                        if (day == null || day < 1 || day > 31) {
                            continue;
                        }
                        String stamp = dayEntry.getValue().isJsonPrimitive()
                                ? dayEntry.getValue().getAsString() : "";
                        days.put(day, stamp);
                    }
                    data.years.put(year, days);
                }
            }

            if (json.has("last_notified") && json.get("last_notified").isJsonObject()) {
                for (Map.Entry<String, JsonElement> notifiedEntry : json.getAsJsonObject("last_notified").entrySet()) {
                    Integer year = parseInt(notifiedEntry.getKey());
                    Integer day = parseInt(notifiedEntry.getValue().getAsString());
                    if (year != null && day != null) {
                        data.lastNotified.put(year, day);
                    }
                }
            }

            if (json.has("stocking") && json.get("stocking").isJsonArray()) {
                for (JsonElement element : json.getAsJsonArray("stocking")) {
                    ItemStack stack = ItemStackJson.fromJson(element);
                    if (!stack.isEmpty()) {
                        data.stocking.add(stack);
                    }
                }
            }
            file.players.put(uuid, data);
        }
        return file;
    }

    private static Integer parseInt(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static UUID parseUuid(String text) {
        try {
            return UUID.fromString(text.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Read-only view used by the admin GUI and {@code /advent list}. */
    public Map<UUID, String> names() {
        Map<UUID, String> result = new LinkedHashMap<>();
        players.forEach((uuid, entry) -> result.put(uuid, entry.name));
        return result;
    }
}
