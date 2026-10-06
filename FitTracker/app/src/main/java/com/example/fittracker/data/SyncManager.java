package com.example.fittracker.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.fittracker.model.ActivityType;
import com.example.fittracker.model.Workout;
import com.example.fittracker.sensor.StepTracker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Keeps the local SQLite data and the signed-in user's Supabase rows in step.
 * The app always reads from SQLite (fast, works offline); every change is also sent to
 * Supabase in the background. If sending fails, a "dirty" flag makes the next app start
 * upload everything again. On login, the user's rows are downloaded into SQLite.
 */
public class SyncManager {

    private static final String TAG = "SyncManager";
    private static final long STEP_FLUSH_DELAY_MS = 60_000;
    private static final String K_DIRTY = "dirty";
    private static final String K_OWNER = "data_owner";

    private static SyncManager instance;

    private final Context app;
    private final AuthManager auth;
    private final SharedPreferences sp;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Integer> pendingSteps = new HashMap<>();
    private boolean stepFlushScheduled;

    public static synchronized SyncManager get(Context context) {
        if (instance == null) instance = new SyncManager(context.getApplicationContext());
        return instance;
    }

    private SyncManager(Context app) {
        this.app = app;
        auth = new AuthManager(app);
        sp = app.getSharedPreferences("sync_state", Context.MODE_PRIVATE);
    }

    // ---------- Background updates (called after each local change) ----------

    public void pushWorkout(Workout w) {
        run(() -> upsert("workouts", "user_id,start_time", new JSONArray().put(workoutJson(w))));
    }

    public void deleteWorkout(long startTime) {
        run(() -> SupabaseClient.rest("DELETE", "workouts?start_time=eq." + startTime, null,
                auth.getAccessToken(), null));
    }

    /** Step totals change every few seconds, so they are batched and sent once a minute. */
    public void queueSteps(String day, int steps) {
        if (!auth.isLoggedIn()) return;
        synchronized (pendingSteps) {
            pendingSteps.put(day, steps);
            if (stepFlushScheduled) return;
            stepFlushScheduled = true;
        }
        main.postDelayed(this::flushSteps, STEP_FLUSH_DELAY_MS);
    }

    public void flushSteps() {
        Map<String, Integer> batch;
        synchronized (pendingSteps) {
            stepFlushScheduled = false;
            if (pendingSteps.isEmpty()) return;
            batch = new HashMap<>(pendingSteps);
            pendingSteps.clear();
        }
        run(() -> upsert("daily_steps", "user_id,day", stepsJson(batch)));
    }

    public void pushProfile() {
        run(this::uploadProfile);
    }

    /** Deletes all of this user's workouts and steps on the server ("Clear all data"). */
    public void clearRemote() {
        run(() -> {
            String token = auth.getAccessToken();
            String uid = auth.getUserId();
            SupabaseClient.rest("DELETE", "workouts?user_id=eq." + uid, null, token, null);
            SupabaseClient.rest("DELETE", "daily_steps?user_id=eq." + uid, null, token, null);
        });
    }

    /** Retries uploads that failed earlier (e.g. while offline). */
    public void retryIfNeeded() {
        if (auth.isLoggedIn() && sp.getBoolean(K_DIRTY, false)) {
            run(this::uploadAll);
        }
    }

    // ---------- Login / logout (blocking, call from a background thread) ----------

    /**
     * Run right after signing in. Data on this phone that belongs to nobody yet (from before
     * accounts existed) or to this same user is uploaded first so nothing is lost; another
     * user's data is wiped. Then the user's rows are downloaded into SQLite.
     */
    public void syncAfterLogin() throws IOException {
        String uid = auth.getUserId();
        String owner = sp.getString(K_OWNER, null);
        if (owner == null || owner.equals(uid)) {
            uploadAll();
        } else {
            wipeLocal();
        }
        download();
        sp.edit().putString(K_OWNER, uid).putBoolean(K_DIRTY, false).apply();
    }

    /** Uploads anything unsent, signs out, and removes this user's data from the phone. */
    public void logout() {
        synchronized (pendingSteps) {
            pendingSteps.clear();
            stepFlushScheduled = false;
        }
        try {
            uploadAll();
        } catch (IOException e) {
            Log.w(TAG, "Final upload before logout failed", e);
        }
        auth.signOut();
        wipeLocal();
        sp.edit().clear().apply();
    }

    // ---------- Internals ----------

    private interface Task {
        void run() throws IOException;
    }

    private void run(Task task) {
        if (!auth.isLoggedIn()) return;
        executor.execute(() -> {
            try {
                task.run();
            } catch (IOException e) {
                Log.w(TAG, "Sync failed, will retry later", e);
                sp.edit().putBoolean(K_DIRTY, true).apply();
            }
        });
    }

    private void uploadAll() throws IOException {
        DatabaseHelper db = DatabaseHelper.get(app);
        JSONArray workouts = new JSONArray();
        for (Workout w : db.getAllWorkouts()) workouts.put(workoutJson(w));
        if (workouts.length() > 0) upsert("workouts", "user_id,start_time", workouts);

        Map<String, Integer> steps = db.getStepsSince("");
        steps.put(StepTracker.todayKey(), StepTracker.readTodaySteps(app));
        // Zero days carry nothing, and sending them could overwrite real totals on the server
        steps.values().removeIf(n -> n <= 0);
        upsert("daily_steps", "user_id,day", stepsJson(steps));

        if (new UserPrefs(app).isDirty()) uploadProfile();
        sp.edit().putBoolean(K_DIRTY, false).apply();
    }

    private void uploadProfile() throws IOException {
        UserPrefs p = new UserPrefs(app);
        try {
            JSONObject o = new JSONObject()
                    .put("id", auth.getUserId())
                    .put("name", p.getName())
                    .put("age", p.getAge())
                    .put("height_cm", p.getHeightCm())
                    .put("weight_kg", p.getWeightKg())
                    .put("step_goal", p.getStepGoal())
                    .put("heart_goal", p.getHeartGoal());
            upsert("profiles", "id", new JSONArray().put(o));
            p.markClean();
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    private void download() throws IOException {
        String token = auth.getAccessToken();
        try {
            JSONArray profiles = new JSONArray(SupabaseClient.rest("GET",
                    "profiles?select=*&id=eq." + auth.getUserId(), null, token, null));
            if (profiles.length() > 0) {
                JSONObject p = profiles.getJSONObject(0);
                new UserPrefs(app).saveFromServer(p.getString("name"), p.getInt("age"),
                        (float) p.getDouble("height_cm"), (float) p.getDouble("weight_kg"),
                        p.getInt("step_goal"), p.getInt("heart_goal"));
            } else {
                uploadProfile(); // first login: create the row from what is on the phone
            }

            JSONArray rows = new JSONArray(SupabaseClient.rest("GET",
                    "workouts?select=*&order=start_time.desc", null, token, null));
            List<Workout> workouts = new ArrayList<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject r = rows.getJSONObject(i);
                Workout w = new Workout();
                w.type = ActivityType.fromName(r.getString("type"));
                w.startTime = r.getLong("start_time");
                w.durationSec = r.getLong("duration_sec");
                w.distanceM = r.getDouble("distance_m");
                w.calories = r.getDouble("calories");
                w.heartPoints = r.getInt("heart_points");
                w.steps = r.getInt("steps");
                workouts.add(w);
            }

            JSONArray stepRows = new JSONArray(SupabaseClient.rest("GET",
                    "daily_steps?select=day,steps", null, token, null));
            Map<String, Integer> steps = new HashMap<>();
            for (int i = 0; i < stepRows.length(); i++) {
                JSONObject r = stepRows.getJSONObject(i);
                steps.put(r.getString("day"), r.getInt("steps"));
            }

            DatabaseHelper.get(app).replaceAll(workouts, steps);
            StepTracker.reset(app); // reload today's total from the downloaded rows
        } catch (JSONException e) {
            throw new IOException("Unexpected data from Supabase", e);
        }
    }

    private void wipeLocal() {
        DatabaseHelper.get(app).clearLocal();
        StepTracker.resetAll(app);
        new UserPrefs(app).clear();
    }

    private void upsert(String table, String conflictColumns, JSONArray rows) throws IOException {
        if (rows.length() == 0) return;
        SupabaseClient.rest("POST", table + "?on_conflict=" + conflictColumns, rows.toString(),
                auth.getAccessToken(), "resolution=merge-duplicates,return=minimal");
    }

    private JSONObject workoutJson(Workout w) {
        try {
            return new JSONObject()
                    .put("user_id", auth.getUserId())
                    .put("type", w.type.name())
                    .put("start_time", w.startTime)
                    .put("duration_sec", w.durationSec)
                    .put("distance_m", w.distanceM)
                    .put("calories", w.calories)
                    .put("heart_points", w.heartPoints)
                    .put("steps", w.steps);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private JSONArray stepsJson(Map<String, Integer> steps) {
        JSONArray rows = new JSONArray();
        try {
            for (Map.Entry<String, Integer> e : steps.entrySet()) {
                rows.put(new JSONObject()
                        .put("user_id", auth.getUserId())
                        .put("day", e.getKey())
                        .put("steps", e.getValue()));
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        return rows;
    }
}
