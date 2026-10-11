package com.adventcalendar.util;

import com.adventcalendar.AdventCalendar;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Turns "a username or a skin URL" into the data needed for a {@code minecraft:player_head}
 * reward ({@code SkullOwner} NBT with a {@code textures} property).
 *
 * <p>Server safe: it only uses plain HTTP + authlib, never client classes, so dedicated
 * servers can resolve heads as well.</p>
 */
public final class SkinProfiles {
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final Map<String, Result> CACHE = new ConcurrentHashMap<>();
    private static final Result MISSING = new Result(null, null, null, null);

    private SkinProfiles() {
    }

    /** Resolved profile data. Any field may be {@code null} when resolution failed. */
    public static final class Result {
        public final String name;
        public final UUID uuid;
        public final String textureValue;
        public final String textureSignature;

        Result(String name, UUID uuid, String textureValue, String textureSignature) {
            this.name = name;
            this.uuid = uuid;
            this.textureValue = textureValue;
            this.textureSignature = textureSignature;
        }

        public boolean isUsable() {
            return textureValue != null;
        }

        public GameProfile asGameProfile() {
            GameProfile profile = new GameProfile(uuid != null ? uuid : offlineUuid(name == null ? "Advent" : name),
                    name == null ? "Advent" : name);
            if (textureValue != null) {
                profile.getProperties().put("textures",
                        textureSignature == null
                                ? new Property("textures", textureValue)
                                : new Property("textures", textureValue, textureSignature));
            }
            return profile;
        }

        public static Result ofUrl(String url) {
            return new Result("Advent", null, texturesValueForUrl(url), null);
        }
    }

    public static boolean isUrl(String value) {
        return value != null && (value.startsWith("http://") || value.startsWith("https://"));
    }

    public static boolean looksLikeUsername(String value) {
        return value != null && USERNAME.matcher(value).matches();
    }

    /** Base64 {@code textures} property for a raw skin URL (what the client will download). */
    public static String texturesValueForUrl(String url) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /** Deterministic UUID for accounts that never joined a server. */
    public static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Resolves a username or a URL. Results (including failures) are cached, so this is cheap
     * to call from a server tick loop, but it may perform a blocking HTTP request - call it
     * from the mod's IO thread.
     */
    public static Result resolve(String input) {
        if (input == null || input.isBlank()) {
            return MISSING;
        }
        String value = input.trim();
        return CACHE.computeIfAbsent(value, SkinProfiles::resolveUncached);
    }

    private static Result resolveUncached(String value) {
        if (isUrl(value)) {
            return Result.ofUrl(value);
        }
        if (!looksLikeUsername(value)) {
            return MISSING;
        }
        try {
            int timeout = Math.max(1000, AdventConfig.SKIN_TIMEOUT_MS.get());
            String profileJson = Http.getString(
                    "https://api.mojang.com/users/profiles/minecraft/" + value, timeout);
            if (profileJson == null) {
                return MISSING;
            }
            JsonObject profile = JsonParser.parseString(profileJson).getAsJsonObject();
            String rawId = profile.get("id").getAsString();
            String name = profile.has("name") ? profile.get("name").getAsString() : value;

            String sessionJson = Http.getString(
                    "https://sessionserver.mojang.com/session/minecraft/profile/" + rawId, timeout);
            if (sessionJson == null) {
                return MISSING;
            }
            JsonObject session = JsonParser.parseString(sessionJson).getAsJsonObject();
            JsonArray properties = session.getAsJsonArray("properties");
            if (properties == null) {
                return MISSING;
            }
            for (JsonElement element : properties) {
                JsonObject property = element.getAsJsonObject();
                if ("textures".equals(property.get("name").getAsString())) {
                    String signature = property.has("signature") ? property.get("signature").getAsString() : null;
                    return new Result(name, uuidFromUndashed(rawId), property.get("value").getAsString(), signature);
                }
            }
        } catch (Exception e) {
            AdventCalendar.LOGGER.warn("Could not resolve skin '{}': {}", value, e.toString());
        }
        return MISSING;
    }

    @Nullable
    private static UUID uuidFromUndashed(String raw) {
        try {
            if (raw.length() != 32) {
                return null;
            }
            String dashed = raw.substring(0, 8) + "-" + raw.substring(8, 12) + "-" + raw.substring(12, 16)
                    + "-" + raw.substring(16, 20) + "-" + raw.substring(20);
            return UUID.fromString(dashed);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
