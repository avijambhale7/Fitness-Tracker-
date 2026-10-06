package com.example.fittracker.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.fittracker.model.ActivityType;
import com.example.fittracker.model.Workout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** SQLite storage for workouts and daily step totals. Changes are synced by {@link SyncManager}. */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "fittracker.db";
    private static final int DB_VERSION = 1;

    private static final String T_WORKOUTS = "workouts";
    private static final String T_STEPS = "daily_steps";

    private static DatabaseHelper instance;

    private final Context appContext;

    public static synchronized DatabaseHelper get(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        appContext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_WORKOUTS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "type TEXT NOT NULL, "
                + "start_time INTEGER NOT NULL, "
                + "duration_sec INTEGER NOT NULL, "
                + "distance_m REAL DEFAULT 0, "
                + "calories REAL DEFAULT 0, "
                + "heart_points INTEGER DEFAULT 0, "
                + "steps INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE " + T_STEPS + " ("
                + "day TEXT PRIMARY KEY, "
                + "steps INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + T_WORKOUTS);
        db.execSQL("DROP TABLE IF EXISTS " + T_STEPS);
        onCreate(db);
    }

    // ---------- Workouts ----------

    public long insertWorkout(Workout w) {
        ContentValues cv = new ContentValues();
        cv.put("type", w.type.name());
        cv.put("start_time", w.startTime);
        cv.put("duration_sec", w.durationSec);
        cv.put("distance_m", w.distanceM);
        cv.put("calories", w.calories);
        cv.put("heart_points", w.heartPoints);
        cv.put("steps", w.steps);
        w.id = getWritableDatabase().insert(T_WORKOUTS, null, cv);
        SyncManager.get(appContext).pushWorkout(w);
        return w.id;
    }

    public void deleteWorkout(long id) {
        String[] args = {String.valueOf(id)};
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT start_time FROM " + T_WORKOUTS + " WHERE id = ?", args)) {
            if (c.moveToFirst()) SyncManager.get(appContext).deleteWorkout(c.getLong(0));
        }
        getWritableDatabase().delete(T_WORKOUTS, "id = ?", args);
    }

    public List<Workout> getAllWorkouts() {
        return readWorkouts(getReadableDatabase().query(T_WORKOUTS, null, null, null,
                null, null, "start_time DESC"));
    }

    public List<Workout> getRecentWorkouts(int limit) {
        return readWorkouts(getReadableDatabase().query(T_WORKOUTS, null, null, null,
                null, null, "start_time DESC", String.valueOf(limit)));
    }

    /** Workouts that started in [from, to). */
    public List<Workout> getWorkoutsBetween(long from, long to) {
        return readWorkouts(getReadableDatabase().query(T_WORKOUTS, null,
                "start_time >= ? AND start_time < ?",
                new String[]{String.valueOf(from), String.valueOf(to)},
                null, null, "start_time DESC"));
    }

    private List<Workout> readWorkouts(Cursor c) {
        List<Workout> list = new ArrayList<>();
        try {
            while (c.moveToNext()) {
                Workout w = new Workout();
                w.id = c.getLong(c.getColumnIndexOrThrow("id"));
                w.type = ActivityType.fromName(c.getString(c.getColumnIndexOrThrow("type")));
                w.startTime = c.getLong(c.getColumnIndexOrThrow("start_time"));
                w.durationSec = c.getLong(c.getColumnIndexOrThrow("duration_sec"));
                w.distanceM = c.getDouble(c.getColumnIndexOrThrow("distance_m"));
                w.calories = c.getDouble(c.getColumnIndexOrThrow("calories"));
                w.heartPoints = c.getInt(c.getColumnIndexOrThrow("heart_points"));
                w.steps = c.getInt(c.getColumnIndexOrThrow("steps"));
                list.add(w);
            }
        } finally {
            c.close();
        }
        return list;
    }

    // ---------- Steps ----------

    public void saveSteps(String day, int steps) {
        ContentValues cv = new ContentValues();
        cv.put("day", day);
        cv.put("steps", steps);
        getWritableDatabase().insertWithOnConflict(T_STEPS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        SyncManager.get(appContext).queueSteps(day, steps);
    }

    public int getSteps(String day) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT steps FROM " + T_STEPS + " WHERE day = ?", new String[]{day})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    /** Daily step totals keyed by day, for every day on or after {@code fromDay}. */
    public Map<String, Integer> getStepsSince(String fromDay) {
        Map<String, Integer> out = new HashMap<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT day, steps FROM " + T_STEPS + " WHERE day >= ?", new String[]{fromDay})) {
            while (c.moveToNext()) out.put(c.getString(0), c.getInt(1));
        }
        return out;
    }

    /** "Clear all data": deletes everything here and on the server. */
    public void clearAll() {
        clearLocal();
        SyncManager.get(appContext).clearRemote();
    }

    /** Deletes everything on this phone only (used on logout). */
    public void clearLocal() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(T_WORKOUTS, null, null);
        db.delete(T_STEPS, null, null);
    }

    /** Replaces the local copy with data downloaded from Supabase (no upload back). */
    public void replaceAll(List<Workout> workouts, Map<String, Integer> steps) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(T_WORKOUTS, null, null);
            db.delete(T_STEPS, null, null);
            for (Workout w : workouts) {
                ContentValues cv = new ContentValues();
                cv.put("type", w.type.name());
                cv.put("start_time", w.startTime);
                cv.put("duration_sec", w.durationSec);
                cv.put("distance_m", w.distanceM);
                cv.put("calories", w.calories);
                cv.put("heart_points", w.heartPoints);
                cv.put("steps", w.steps);
                db.insert(T_WORKOUTS, null, cv);
            }
            for (Map.Entry<String, Integer> e : steps.entrySet()) {
                ContentValues cv = new ContentValues();
                cv.put("day", e.getKey());
                cv.put("steps", e.getValue());
                db.insert(T_STEPS, null, cv);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
