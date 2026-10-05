package com.example.fittracker.model;

import com.example.fittracker.util.FitCalc;

public class Workout {
    public long id;
    public ActivityType type;
    public long startTime;
    public long durationSec;
    public double distanceM;
    public double calories;
    public int heartPoints;
    public int steps;

    public static Workout create(ActivityType type, long startTime, long durationSec,
                                 double distanceM, int steps, double weightKg) {
        Workout w = new Workout();
        w.type = type;
        w.startTime = startTime;
        w.durationSec = durationSec;
        w.distanceM = distanceM;
        w.steps = steps;
        w.calories = FitCalc.workoutCalories(type, weightKg, durationSec);
        w.heartPoints = FitCalc.heartPoints(type, durationSec);
        return w;
    }
}
