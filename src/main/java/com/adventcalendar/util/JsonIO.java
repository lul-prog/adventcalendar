package com.adventcalendar.util;

import com.adventcalendar.AdventCalendar;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Small JSON helper. Files are written pretty printed (so they stay hand editable) and
 * atomically (write to a temporary file, then move it over the real one).
 */
public final class JsonIO {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    private JsonIO() {
    }

    public static Gson gson() {
        return GSON;
    }

    public static String pretty(JsonElement element) {
        return GSON.toJson(element);
    }

    /** Reads a JSON object, returning {@code null} when the file is missing or broken. */
    public static JsonObject read(Path file) {
        try {
            if (!Files.exists(file)) {
                return null;
            }
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            if (text.isBlank()) {
                return null;
            }
            JsonElement parsed = JsonParser.parseString(text);
            if (parsed.isJsonObject()) {
                return parsed.getAsJsonObject();
            }
            AdventCalendar.LOGGER.warn("{} does not contain a JSON object, ignoring it", file);
        } catch (IOException | RuntimeException e) {
            AdventCalendar.LOGGER.error("Could not read {}: {}", file, e.toString());
        }
        return null;
    }

    /** Writes a JSON object atomically. Returns {@code true} on success. */
    public static boolean write(Path file, JsonObject json) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
            Files.write(tmp, pretty(json).getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            AdventCalendar.LOGGER.error("Could not write {}: {}", file, e.toString());
            return false;
        }
    }

    public static String string(JsonObject json, String key, String fallback) {
        if (json == null || !json.has(key)) {
            return fallback;
        }
        JsonElement element = json.get(key);
        return element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    public static boolean bool(JsonObject json, String key, boolean fallback) {
        if (json == null || !json.has(key)) {
            return fallback;
        }
        JsonElement element = json.get(key);
        try {
            return element.getAsBoolean();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public static int intValue(JsonObject json, String key, int fallback) {
        if (json == null || !json.has(key)) {
            return fallback;
        }
        try {
            return json.get(key).getAsInt();
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
