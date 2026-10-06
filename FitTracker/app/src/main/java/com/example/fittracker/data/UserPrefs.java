package com.example.fittracker.data;

import android.content.Context;
import android.content.SharedPreferences;

/** User profile and daily goals, stored in SharedPreferences. */
public class UserPrefs {

    private final Context context;
    private final SharedPreferences sp;

    public UserPrefs(Context context) {
        this.context = context.getApplicationContext();
        sp = context.getApplicationContext().getSharedPreferences("user_profile", Context.MODE_PRIVATE);
    }

    public String getName() {
        return sp.getString("name", "Fit User");
    }

    public int getAge() {
        return sp.getInt("age", 21);
    }

    public float getHeightCm() {
        return sp.getFloat("height", 170f);
    }

    public float getWeightKg() {
        return sp.getFloat("weight", 65f);
    }

    public int getStepGoal() {
        return sp.getInt("step_goal", 8000);
    }

    public int getHeartGoal() {
        return sp.getInt("heart_goal", 30);
    }

    /** Saves changes made on this phone and uploads them to Supabase. */
    public void save(String name, int age, float heightCm, float weightKg, int stepGoal, int heartGoal) {
        write(name, age, heightCm, weightKg, stepGoal, heartGoal, true);
        SyncManager.get(context).pushProfile();
    }

    /** Stores the profile downloaded from Supabase (no upload back). */
    public void saveFromServer(String name, int age, float heightCm, float weightKg, int stepGoal, int heartGoal) {
        write(name, age, heightCm, weightKg, stepGoal, heartGoal, false);
    }

    /**
     * True when the profile was edited here and not uploaded yet. A profile saved before
     * accounts existed (has a name but no flag) also counts, so it gets uploaded once.
     */
    public boolean isDirty() {
        return sp.getBoolean("dirty", sp.contains("name"));
    }

    public void markClean() {
        sp.edit().putBoolean("dirty", false).apply();
    }

    public void clear() {
        sp.edit().clear().apply();
    }

    private void write(String name, int age, float heightCm, float weightKg, int stepGoal,
                       int heartGoal, boolean dirty) {
        sp.edit()
                .putString("name", name)
                .putInt("age", age)
                .putFloat("height", heightCm)
                .putFloat("weight", weightKg)
                .putInt("step_goal", stepGoal)
                .putInt("heart_goal", heartGoal)
                .putBoolean("dirty", dirty)
                .apply();
    }
}
