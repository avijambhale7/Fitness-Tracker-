package com.example.fittracker.data;

import com.example.fittracker.BuildConfig;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Minimal Supabase client over HTTPS (Auth + PostgREST), so the app needs no extra libraries.
 * The project URL and anon key come from local.properties (see README). Blocking calls:
 * never run these on the main thread.
 */
public final class SupabaseClient {

    /** A non-2xx reply from Supabase, with the server's message. */
    public static class ApiException extends IOException {
        public final int status;

        ApiException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private SupabaseClient() {
    }

    public static boolean isConfigured() {
        return !BuildConfig.SUPABASE_URL.isEmpty() && !BuildConfig.SUPABASE_ANON_KEY.isEmpty();
    }

    /** POST to /auth/v1/{path}. {@code bearer} may be null. */
    public static JSONObject auth(String path, JSONObject body, String bearer) throws IOException {
        String reply = send("POST", "/auth/v1/" + path, body.toString(), bearer, null);
        try {
            return reply.isEmpty() ? new JSONObject() : new JSONObject(reply);
        } catch (Exception e) {
            throw new IOException("Unexpected reply from Supabase", e);
        }
    }

    /** Calls /rest/v1/{pathAndQuery} as the signed-in user and returns the response body. */
    public static String rest(String method, String pathAndQuery, String body, String accessToken,
                              String prefer) throws IOException {
        return send(method, "/rest/v1/" + pathAndQuery, body, accessToken, prefer);
    }

    private static String send(String method, String path, String body, String bearer,
                               String prefer) throws IOException {
        if (!isConfigured()) {
            throw new IOException("Supabase is not set up. Add supabase.url and supabase.anonKey "
                    + "to local.properties and rebuild.");
        }
        String base = BuildConfig.SUPABASE_URL.replaceAll("/+$", "");
        HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
        try {
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setRequestMethod(method);
            c.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY);
            c.setRequestProperty("Accept", "application/json");
            if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
            if (prefer != null) c.setRequestProperty("Prefer", prefer);
            if (body != null) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                c.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream out = c.getOutputStream()) {
                    out.write(bytes);
                }
            }
            int status = c.getResponseCode();
            String reply = read(status >= 400 ? c.getErrorStream() : c.getInputStream());
            if (status >= 400) throw new ApiException(status, errorMessage(reply, status));
            return reply;
        } finally {
            c.disconnect();
        }
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) return "";
        try (InputStream is = in) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = is.read(chunk)) != -1) buf.write(chunk, 0, n);
            return buf.toString("UTF-8");
        }
    }

    /** Supabase Auth and PostgREST use different field names for the error text. */
    private static String errorMessage(String reply, int status) {
        try {
            JSONObject o = new JSONObject(reply);
            for (String key : new String[]{"msg", "error_description", "message", "error"}) {
                String s = o.optString(key, "");
                if (!s.isEmpty()) return s;
            }
        } catch (Exception ignored) {
        }
        return "Server error " + status;
    }
}
