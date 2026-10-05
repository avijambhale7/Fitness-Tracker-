package com.example.fittracker.util;

import com.example.fittracker.model.ActivityType;

/** Formulas used to estimate fitness metrics. */
public final class FitCalc {

    private FitCalc() {
    }

    /** Calories = MET x weight (kg) x hours. */
    public static double workoutCalories(ActivityType type, double weightKg, long seconds) {
        return type.met * weightKg * seconds / 3600.0;
    }

    public static int heartPoints(ActivityType type, long seconds) {
        return (int) (seconds / 60) * type.heartPointsPerMinute;
    }

    /** Average stride length is roughly 41.5% of body height. */
    public static double strideMeters(double heightCm) {
        return heightCm * 0.415 / 100.0;
    }

    public static double stepsToMeters(int steps, double heightCm) {
        return steps * strideMeters(heightCm);
    }

    /** About 0.04 kcal per step for a 70 kg person, scaled by body weight. */
    public static double stepCalories(int steps, double weightKg) {
        return steps * 0.04 * (weightKg / 70.0);
    }

    /** Roughly 120 steps are taken per minute of walking. */
    public static int moveMinutesFromSteps(int steps) {
        return steps / 120;
    }

    public static double bmi(double weightKg, double heightCm) {
        double m = heightCm / 100.0;
        return m <= 0 ? 0 : weightKg / (m * m);
    }

    public static String bmiCategory(double bmi) {
        if (bmi <= 0) return "-";
        if (bmi < 18.5) return "Underweight";
        if (bmi < 25) return "Healthy weight";
        if (bmi < 30) return "Overweight";
        return "Obese";
    }
}
