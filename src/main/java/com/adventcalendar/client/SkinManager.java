package com.adventcalendar.client;

import com.adventcalendar.AdventCalendar;
import com.adventcalendar.AdventConfig;
import com.adventcalendar.util.Http;
import com.adventcalendar.util.SkinProfiles;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Downloads, caches and registers the custom player skins used by the Advent NPC.
 *
 * <p>Accepted values for the "skin" field of an NPC:</p>
 * <ul>
 *     <li>{@code https://example.com/skin.png} - any direct PNG URL</li>
 *     <li>{@code Notch} - a Minecraft username, resolved through the Mojang API</li>
 *     <li>{@code local:myskin.png} - a file dropped into
 *     {@code config/adventcalendar/skins/}</li>
 * </ul>
 *
 * <p>The PNG is stored as {@code config/adventcalendar/skins/<sha1 of the value>.png} so it
 * only has to be downloaded once; while the download is running the NPC simply uses the
 * default Steve/Alex skin.</p>
 */
public final class SkinManager {
    public static final ResourceLocation WIDE_STEVE =
            new ResourceLocation("textures/entity/player/wide/steve.png");
    public static final ResourceLocation SLIM_ALEX =
            new ResourceLocation("textures/entity/player/slim/alex.png");

    /** State of one skin value, polled by the NPC configuration GUI. */
    public enum SkinState {
        EMPTY,
        DISABLED,
        PENDING,
        LOADING,
        READY,
        FAILED
    }

    private static final Map<String, SkinState> STATES = new ConcurrentHashMap<>();
    private static final Map<String, ResourceLocation> TEXTURES = new ConcurrentHashMap<>();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "AdventCalendar-Skins");
        thread.setDaemon(true);
        return thread;
    });

    private SkinManager() {
    }

    public static Path skinsDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve("adventcalendar").resolve("skins");
    }

    @Nullable
    public static ResourceLocation textureFor(String skin) {
        if (skin == null || skin.isBlank()) {
            return null;
        }
        String key = skin.trim();
        ResourceLocation ready = TEXTURES.get(key);
        if (ready != null) {
            return ready;
        }
        SkinState state = STATES.get(key);
        if (state == null) {
            request(key);
        } else if (state == SkinState.PENDING) {
            // still in the queue, nothing left to do
        }
        return null;
    }

    public static SkinState stateOf(String skin) {
        if (skin == null || skin.isBlank()) {
            return SkinState.EMPTY;
        }
        String key = skin.trim();
        SkinState state = STATES.get(key);
        if (state == null) {
            request(key);
            return SkinState.PENDING;
        }
        return state;
    }

    /** Queue the download/registration of a skin value; safe to call every frame. */
    public static void request(String skin) {
        if (skin == null || skin.isBlank()) {
            return;
        }
        String key = skin.trim();
        if (STATES.containsKey(key) || TEXTURES.containsKey(key)) {
            return;
        }
        if (STATES.putIfAbsent(key, SkinState.PENDING) != null) {
            return;
        }
        if (!AdventConfig.ALLOW_REMOTE_SKINS.get() && !key.startsWith("local:")) {
            STATES.put(key, SkinState.DISABLED);
            return;
        }
        EXECUTOR.execute(() -> resolve(key));
    }

    private static void resolve(String key) {
        try {
            Path directory = skinsDirectory();
            Files.createDirectories(directory);
            String fileName = hash(key) + ".png";
            Path file = directory.resolve(fileName);

            if (!Files.exists(file)) {
                if (!download(key, file)) {
                    STATES.put(key, SkinState.FAILED);
                    return;
                }
            }
            STATES.put(key, SkinState.LOADING);
            Minecraft.getInstance().execute(() -> register(key, file));
        } catch (Exception e) {
            AdventCalendar.LOGGER.warn("Could not prepare skin '{}': {}", key, e.toString());
            STATES.put(key, SkinState.FAILED);
        }
    }

    private static boolean download(String key, Path target) {
        try {
            int timeout = Math.max(1000, AdventConfig.SKIN_TIMEOUT_MS.get());
            int maxBytes = Math.max(8, AdventConfig.SKIN_MAX_KB.get()) * 1024;
            byte[] data;
            if (key.startsWith("local:")) {
                String name = key.substring("local:".length()).trim();
                Path source = skinsDirectory().resolve(name);
                if (!Files.exists(source)) {
                    AdventCalendar.LOGGER.warn("Local skin '{}' does not exist in {}", name, skinsDirectory());
                    return false;
                }
                data = Files.readAllBytes(source);
            } else if (SkinProfiles.isUrl(key)) {
                data = Http.getBytes(key, timeout, maxBytes);
            } else if (SkinProfiles.looksLikeUsername(key)) {
                String url = resolveUsernameSkinUrl(key, timeout);
                if (url == null) {
                    return false;
                }
                data = Http.getBytes(url, timeout, maxBytes);
            } else {
                AdventCalendar.LOGGER.warn("Skin '{}' is neither a URL, a username nor a local: file", key);
                return false;
            }
            if (data == null || data.length == 0) {
                return false;
            }
            Path temporary = target.resolveSibling(target.getFileName() + ".part");
            Files.write(temporary, data);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception e) {
            AdventCalendar.LOGGER.warn("Could not download skin '{}': {}", key, e.toString());
            return false;
        }
    }

    @Nullable
    private static String resolveUsernameSkinUrl(String username, int timeout) {
        try {
            String profileJson = Http.getString(
                    "https://api.mojang.com/users/profiles/minecraft/" + username, timeout);
            if (profileJson == null) {
                return null;
            }
            JsonObject profile = JsonParser.parseString(profileJson).getAsJsonObject();
            String id = profile.get("id").getAsString();
            String sessionJson = Http.getString(
                    "https://sessionserver.mojang.com/session/minecraft/profile/" + id, timeout);
            if (sessionJson == null) {
                return null;
            }
            JsonObject session = JsonParser.parseString(sessionJson).getAsJsonObject();
            JsonArray properties = session.getAsJsonArray("properties");
            if (properties == null) {
                return null;
            }
            for (JsonElement element : properties) {
                JsonObject property = element.getAsJsonObject();
                if (!"textures".equals(property.get("name").getAsString())) {
                    continue;
                }
                String decoded = new String(
                        Base64.getDecoder().decode(property.get("value").getAsString()), StandardCharsets.UTF_8);
                return JsonParser.parseString(decoded).getAsJsonObject()
                        .getAsJsonObject("textures").getAsJsonObject("SKIN")
                        .get("url").getAsString();
            }
        } catch (Exception e) {
            AdventCalendar.LOGGER.warn("Could not resolve the skin of '{}': {}", username, e.toString());
        }
        return null;
    }

    private static void register(String key, Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(in);
            int width = image.getWidth();
            int height = image.getHeight();
            if (width < 64 || height < 32) {
                image.close();
                AdventCalendar.LOGGER.warn("Skin '{}' is too small ({}x{}), expected 64x64 or 64x32",
                        key, width, height);
                STATES.put(key, SkinState.FAILED);
                return;
            }
            ResourceLocation location = new ResourceLocation(AdventCalendar.MOD_ID, "skins/" + hash(key));
            Minecraft minecraft = Minecraft.getInstance();
            DynamicTexture texture = new DynamicTexture(image);
            minecraft.getTextureManager().register(location, texture);
            TEXTURES.put(key, location);
            STATES.put(key, SkinState.READY);
        } catch (Exception e) {
            AdventCalendar.LOGGER.warn("Skin file of '{}' could not be read: {}", key, e.toString());
            STATES.put(key, SkinState.FAILED);
        }
    }

    /** Forgets a skin (used by the "reload skin" button of the NPC GUI). */
    public static void invalidate(String skin) {
        if (skin == null || skin.isBlank()) {
            return;
        }
        String key = skin.trim();
        STATES.remove(key);
        TEXTURES.remove(key);
        try {
            Files.deleteIfExists(skinsDirectory().resolve(hash(key) + ".png"));
        } catch (Exception e) {
            AdventCalendar.LOGGER.debug("Could not delete the cached skin of '{}': {}", key, e.toString());
        }
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            StringBuilder builder = new StringBuilder();
            for (byte b : digest.digest(value.getBytes(StandardCharsets.UTF_8))) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
