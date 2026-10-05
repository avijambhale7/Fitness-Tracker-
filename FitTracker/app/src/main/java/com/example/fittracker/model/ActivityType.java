package com.example.fittracker.model;

/**
 * Supported activity types. MET values are used to estimate calories and
 * heartPointsPerMinute follows Google Fit's rule: 1 point per minute of
 * moderate activity, 2 points per minute of vigorous activity.
 */
public enum ActivityType {
    WALKING("Walking", "🚶", 3.5, 1, true, 0, 0xFF22C7A0),
    RUNNING("Running", "🏃", 9.8, 2, true, 0, 0xFFFF6B5A),
    CYCLING("Cycling", "🚴", 7.5, 2, false, 16, 0xFF0EA5E9),
    HIKING("Hiking", "🥾", 6.0, 2, true, 0, 0xFF65A30D),
    SWIMMING("Swimming", "🏊", 8.0, 2, false, 2, 0xFF3B82F6),
    YOGA("Yoga", "🧘", 2.5, 1, false, 0, 0xFFA78BFA),
    STRENGTH("Strength training", "🏋", 5.0, 1, false, 0, 0xFFF59E0B),
    DANCING("Dancing", "💃", 5.5, 1, false, 0, 0xFFEC4899),
    HIIT("HIIT", "🔥", 8.0, 2, false, 0, 0xFFEF4444),
    OTHER("Other", "⭐", 4.0, 1, false, 0, 0xFF7C5CFA);

    public final String label;
    public final String emoji;
    public final double met;
    public final int heartPointsPerMinute;
    /** True when distance is measured from the step sensor. */
    public final boolean usesSteps;
    /** Average speed used to estimate distance for non-step activities (0 = no distance). */
    public final double avgSpeedKmh;
    /** Accent colour used for this activity in lists and on the workout screen. */
    public final int color;

    ActivityType(String label, String emoji, double met, int heartPointsPerMinute,
                 boolean usesSteps, double avgSpeedKmh, int color) {
        this.label = label;
        this.emoji = emoji;
        this.met = met;
        this.heartPointsPerMinute = heartPointsPerMinute;
        this.usesSteps = usesSteps;
        this.avgSpeedKmh = avgSpeedKmh;
        this.color = color;
    }

    public static String[] labels() {
        ActivityType[] all = values();
        String[] out = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            out[i] = all[i].emoji + "  " + all[i].label;
        }
        return out;
    }

    public static ActivityType fromName(String name) {
        try {
            return valueOf(name);
        } catch (Exception e) {
            return OTHER;
        }
    }
}
