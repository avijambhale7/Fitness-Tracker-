package com.example.fittracker.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class DateUtil {

    private DateUtil() {
    }

    public static String dayKey(long time) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(time));
    }

    public static long startOfDay(long time) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(time);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    public static long endOfDay(long time) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(startOfDay(time));
        c.add(Calendar.DAY_OF_YEAR, 1);
        return c.getTimeInMillis();
    }

    public static long daysAgo(int days) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -days);
        return c.getTimeInMillis();
    }

    /** 01:05:09 style stopwatch text. */
    public static String timer(long seconds) {
        return String.format(Locale.US, "%02d:%02d:%02d",
                seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    /** "45 min" or "1 h 5 min". */
    public static String duration(long seconds) {
        long min = seconds / 60;
        if (min < 1) return seconds + " sec";
        if (min < 60) return min + " min";
        return (min / 60) + " h " + (min % 60) + " min";
    }

    public static String dateTime(long time) {
        return new SimpleDateFormat("EEE, d MMM · h:mm a", Locale.getDefault()).format(new Date(time));
    }

    public static String fullDate(long time) {
        return new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(new Date(time));
    }

    public static String shortDay(long time) {
        return new SimpleDateFormat("EEE", Locale.getDefault()).format(new Date(time));
    }
}
