package com.example.fittracker.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Supabase email/password accounts. The session (access + refresh token) is kept in
 * SharedPreferences so the user stays signed in. Methods that talk to the server block:
 * call them from a background thread.
 */
public class AuthManager {

    private static final String K_ACCESS = "access_token";
    private static final String K_REFRESH = "refresh_token";
    private static final String K_EXPIRES = "expires_at";
    private static final String K_USER_ID = "user_id";
    private static final String K_EMAIL = "email";

    private static final Object LOCK = new Object();

    private final SharedPreferences sp;

    public AuthManager(Context context) {
        sp = context.getApplicationContext().getSharedPreferences("supabase_session", Context.MODE_PRIVATE);
    }

    public boolean isLoggedIn() {
        return sp.getString(K_REFRESH, null) != null;
    }

    /** Email of the signed-in user, or null. */
    public String getCurrentEmail() {
        return sp.getString(K_EMAIL, null);
    }

    /** Supabase user id (uuid) of the signed-in user, or null. */
    public String getUserId() {
        return sp.getString(K_USER_ID, null);
    }

    /**
     * Creates an account. Returns true if the user is now signed in, or false if Supabase
     * sent a confirmation email first (the user must confirm, then log in).
     */
    public boolean signUp(String email, String password, String name) throws IOException {
        try {
            JSONObject body = new JSONObject()
                    .put("email", email)
                    .put("password", password)
                    .put("data", new JSONObject().put("name", name));
            JSONObject reply = SupabaseClient.auth("signup", body, null);
            if (!reply.has("access_token")) return false;
            saveSession(reply);
            return true;
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    public void signIn(String email, String password) throws IOException {
        try {
            JSONObject body = new JSONObject().put("email", email).put("password", password);
            saveSession(SupabaseClient.auth("token?grant_type=password", body, null));
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    /** A valid access token, refreshed first if it is about to expire. */
    public String getAccessToken() throws IOException {
        synchronized (LOCK) {
            String refresh = sp.getString(K_REFRESH, null);
            if (refresh == null) throw new IOException("Not logged in");
            if (System.currentTimeMillis() < sp.getLong(K_EXPIRES, 0) - 60_000) {
                return sp.getString(K_ACCESS, null);
            }
            try {
                JSONObject body = new JSONObject().put("refresh_token", refresh);
                saveSession(SupabaseClient.auth("token?grant_type=refresh_token", body, null));
                return sp.getString(K_ACCESS, null);
            } catch (SupabaseClient.ApiException e) {
                // Refresh token revoked or expired: the user has to log in again
                if (e.status == 400 || e.status == 401) sp.edit().clear().apply();
                throw e;
            } catch (JSONException e) {
                throw new IOException(e);
            }
        }
    }

    /** Ends the session on the server (best effort) and forgets it on this phone. */
    public void signOut() {
        try {
            SupabaseClient.auth("logout", new JSONObject(), getAccessToken());
        } catch (IOException ignored) {
            // Offline or token already invalid: signing out locally is enough
        }
        sp.edit().clear().apply();
    }

    private void saveSession(JSONObject s) throws JSONException {
        JSONObject user = s.getJSONObject("user");
        sp.edit()
                .putString(K_ACCESS, s.getString("access_token"))
                .putString(K_REFRESH, s.getString("refresh_token"))
                .putLong(K_EXPIRES, System.currentTimeMillis() + s.optLong("expires_in", 3600) * 1000)
                .putString(K_USER_ID, user.getString("id"))
                .putString(K_EMAIL, user.optString("email", ""))
                .apply();
    }
}
