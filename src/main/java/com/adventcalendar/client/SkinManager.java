package com.adventcalendar.client;

import com.adventcalendar.AdventCalendar;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Descarga y cachea las skins del NPC.
 * - Si el valor es una URL http(s): descarga el PNG directamente.
 * - Si es un nombre de usuario: lo resuelve vía la API de Mojang.
 * El PNG se guarda en config/adventcalendar/skins/<sha1>.png y se registra
 * como DynamicTexture. Si algo falla, se usa la skin de Steve.
 */
public class SkinManager {
    private static final ResourceLocation STEVE =
            new ResourceLocation("textures/entity/player/wide/steve.png");
    private static final Set<String> READY = ConcurrentHashMap.newKeySet();
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
    private static final Map<String, String> URL_CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    /** Devuelve la textura lista, o Steve mientras se descarga en segundo plano. */
    public static ResourceLocation getSkinTexture(String skin) {
        if (skin == null || skin.isEmpty()) {
            return STEVE;
        }
        String key = sha1(skin);
        ResourceLocation loc = new ResourceLocation(AdventCalendar.MOD_ID, "skins/" + key);
        if (READY.contains(key)) {
            return loc;
        }
        if (PENDING.add(key)) {
            EXECUTOR.execute(() -> download(skin, key, loc));
        }
        return STEVE;
    }

    private static void download(String skin, String key, ResourceLocation loc) {
        try {
            Path dir = FMLPaths.CONFIGDIR.get().resolve("adventcalendar/skins");
            Files.createDirectories(dir);
            Path file = dir.resolve(key + ".png");
            if (!Files.exists(file)) {
                String url = skin.startsWith("http://") || skin.startsWith("https://")
                        ? skin
                        : URL_CACHE.computeIfAbsent(skin, SkinManager::resolveMojangSkin);
                if (url == null) {
                    return; // se queda Steve
                }
                try (InputStream in = new URL(url).openStream()) {
                    Files.copy(in, file);
                }
            }
            // El registro de la textura debe ocurrir en el hilo del cliente.
            Minecraft.getInstance().execute(() -> {
                try (InputStream in = Files.newInputStream(file)) {
                    Minecraft.getInstance().getTextureManager()
                            .register(loc, new DynamicTexture(NativeImage.read(in)));
                    READY.add(key);
                } catch (Exception e) {
                    AdventCalendar.LOGGER.warn("Skin inválida en cache ({}): {}", key, e.getMessage());
                }
            });
        } catch (Exception e) {
            AdventCalendar.LOGGER.warn("No se pudo descargar la skin '{}': {}", skin, e.getMessage());
        }
    }

    /** Nombre de usuario -> URL directa del PNG vía api.mojang.com + sessionserver. */
    private static String resolveMojangSkin(String username) {
        try {
            String profile = httpGet("https://api.mojang.com/users/profiles/minecraft/"
                    + URLEncoder.encode(username, StandardCharsets.UTF_8));
            if (profile == null) {
                return null;
            }
            String id = JsonParser.parseString(profile).getAsJsonObject().get("id").getAsString();
            String session = httpGet("https://sessionserver.mojang.com/session/minecraft/profile/" + id);
            if (session == null) {
                return null;
            }
            for (JsonElement el : JsonParser.parseString(session).getAsJsonObject().getAsJsonArray("properties")) {
                JsonObject prop = el.getAsJsonObject();
                if ("textures".equals(prop.get("name").getAsString())) {
                    String decoded = new String(Base64.getDecoder().decode(
                            prop.get("value").getAsString()), StandardCharsets.UTF_8);
                    return JsonParser.parseString(decoded).getAsJsonObject()
                            .getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
                }
            }
        } catch (Exception e) {
            AdventCalendar.LOGGER.warn("No se pudo resolver el usuario '{}': {}", username, e.getMessage());
        }
        return null;
    }

    private static String httpGet(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "adventcalendar-mod/1.0");
            if (c.getResponseCode() != 200) {
                return null;
            }
            try (InputStream in = c.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static String sha1(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest(s.getBytes(StandardCharsets.UTF_8))) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
