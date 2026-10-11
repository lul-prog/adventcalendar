package com.adventcalendar.util;

import com.adventcalendar.AdventCalendar;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Tiny HTTP helper shared by the skin downloader (client) and the head resolver (server). */
public final class Http {
    public static final String USER_AGENT = "adventcalendar-mod/2.0 (Minecraft Forge 1.20.1)";

    private Http() {
    }

    /** GET returning the body as UTF-8 text, or {@code null} on any failure. */
    @Nullable
    public static String getString(String url, int timeoutMs) {
        byte[] data = getBytes(url, timeoutMs, 1 << 20);
        return data == null ? null : new String(data, StandardCharsets.UTF_8);
    }

    /** GET returning the raw body, or {@code null} when the request failed or was too large. */
    @Nullable
    public static byte[] getBytes(String url, int timeoutMs, int maxBytes) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setInstanceFollowRedirects(true);
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                AdventCalendar.LOGGER.debug("HTTP {} for {}", code, url);
                return null;
            }
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    if (out.size() > maxBytes) {
                        AdventCalendar.LOGGER.warn("Refusing to download {}: larger than {} bytes", url, maxBytes);
                        return null;
                    }
                }
                return out.toByteArray();
            }
        } catch (Exception e) {
            AdventCalendar.LOGGER.debug("HTTP request to {} failed: {}", url, e.toString());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
