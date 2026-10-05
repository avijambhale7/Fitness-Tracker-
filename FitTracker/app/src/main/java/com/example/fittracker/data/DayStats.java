package com.example.fittracker.data;

import android.content.Context;

import com.example.fittracker.model.Workout;
import com.example.fittracker.util.DateUtil;
import com.example.fittracker.util.FitCalc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Totals for a single day: steps + workouts combined, like the Google Fit home screen. */
public class DayStats {

    public int steps;
    public int heartPoints;
    public int moveMinutes;
    public int workouts;
    public double calories;
    public double distanceKm;

    /** True when the step goal or the Heart Points goal was reached. */
    public boolean goalMet(UserPrefs prefs) {
        return steps >= prefs.getStepGoal() || heartPoints >= prefs.getHeartGoal();
    }

    /**
     * Stats for the last {@code days} days, oldest first (the last entry is today).
     * Uses two queries in total, however many days are requested.
     */
    public static DayStats[] lastDays(Context context, int days) {
        DatabaseHelper db = DatabaseHelper.get(context);
        UserPrefs prefs = new UserPrefs(context);
        long from = DateUtil.startOfDay(DateUtil.daysAgo(days - 1));
        long to = DateUtil.endOfDay(System.currentTimeMillis());

        Map<String, Integer> stepsByDay = db.getStepsSince(DateUtil.dayKey(from));
        Map<String, List<Workout>> workoutsByDay = new HashMap<>();
        for (Workout w : db.getWorkoutsBetween(from, to)) {
            String key = DateUtil.dayKey(w.startTime);
            List<Workout> list = workoutsByDay.get(key);
            if (list == null) {
                list = new ArrayList<>();
                workoutsByDay.put(key, list);
            }
            list.add(w);
        }

        DayStats[] out = new DayStats[days];
        for (int i = 0; i < days; i++) {
            String key = DateUtil.dayKey(DateUtil.daysAgo(days - 1 - i));
            Integer steps = stepsByDay.get(key);
            List<Workout> workouts = workoutsByDay.get(key);
            out[i] = compute(steps == null ? 0 : steps,
                    workouts == null ? Collections.emptyList() : workouts, prefs);
        }
        return out;
    }

    private static DayStats compute(int steps, List<Workout> workouts, UserPrefs prefs) {
        DayStats s = new DayStats();
        s.steps = steps;

        int workoutSteps = 0;
        long workoutSeconds = 0;
        double workoutDistanceM = 0;
        for (Workout w : workouts) {
            s.workouts++;
            s.heartPoints += w.heartPoints;
            s.calories += w.calories;
            workoutSeconds += w.durationSec;
            workoutSteps += w.steps;
            // Step-based distance is already part of the daily step count
            if (w.steps == 0) workoutDistanceM += w.distanceM;
        }

        // Steps outside workouts add move minutes and calories on top
        int outsideSteps = Math.max(0, s.steps - workoutSteps);
        s.moveMinutes = (int) (workoutSeconds / 60) + FitCalc.moveMinutesFromSteps(outsideSteps);
        s.calories += FitCalc.stepCalories(outsideSteps, prefs.getWeightKg());
        s.distanceKm = (FitCalc.stepsToMeters(s.steps, prefs.getHeightCm()) + workoutDistanceM) / 1000.0;
        return s;
    }
}
