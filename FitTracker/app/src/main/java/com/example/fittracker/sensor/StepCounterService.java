package com.example.fittracker.sensor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.example.fittracker.MainActivity;
import com.example.fittracker.R;
import com.example.fittracker.data.AuthManager;
import com.example.fittracker.data.UserPrefs;
import com.example.fittracker.util.DateUtil;
import com.example.fittracker.util.FitCalc;

import java.util.Locale;

/**
 * Foreground service that keeps the step sensor registered all the time, so steps are
 * counted (and split correctly at midnight) even when the app is closed, just like
 * Google Fit. Shows a silent ongoing notification with today's progress.
 */
public class StepCounterService extends Service implements StepTracker.Listener {

    private static final String TAG = "StepCounterService";
    private static final String CHANNEL_ID = "step_counter";
    private static final int NOTIFICATION_ID = 1;
    private static final long MIN_NOTIFY_INTERVAL_MS = 1000;

    private StepTracker tracker;
    private NotificationManager notificationManager;
    private long lastNotifyMs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    /** Refreshes the notification right after midnight so it shows 0 for the new day. */
    private final Runnable midnightTick = new Runnable() {
        @Override
        public void run() {
            updateNotification(tracker.getTodaySteps(), true);
            scheduleMidnightTick();
        }
    };

    /** Starts the service if someone is logged in and the step permission is granted. Safe to call repeatedly. */
    public static void start(Context context) {
        if (!StepTracker.hasPermission(context) || !new AuthManager(context).isLoggedIn()) return;
        try {
            ContextCompat.startForegroundService(context, new Intent(context, StepCounterService.class));
        } catch (Exception e) {
            // Android 12+ blocks starting from the background in some situations
            Log.w(TAG, "Could not start step service", e);
        }
    }

    /** Stops counting (on logout). */
    public static void stop(Context context) {
        context.stopService(new Intent(context, StepCounterService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createChannel();
        tracker = new StepTracker(this, this);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                ? ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH : 0;
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID,
                    buildNotification(tracker.getTodaySteps()), type);
        } catch (Exception e) {
            Log.w(TAG, "startForeground failed", e);
            stopSelf();
            return;
        }
        tracker.start();
        scheduleMidnightTick();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Profile/goal changes: redraw the notification with the new goal
        if (tracker != null) updateNotification(tracker.getTodaySteps(), true);
        return START_STICKY;
    }

    @Override
    public void onStepsUpdated(int todaySteps) {
        updateNotification(todaySteps, false);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(midnightTick);
        if (tracker != null) tracker.stop();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void scheduleMidnightTick() {
        long now = System.currentTimeMillis();
        handler.removeCallbacks(midnightTick);
        handler.postDelayed(midnightTick, DateUtil.endOfDay(now) - now + 1000);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Step counter",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Shows today's steps while FitTracker counts in the background");
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void updateNotification(int steps, boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastNotifyMs < MIN_NOTIFY_INTERVAL_MS) return;
        lastNotifyMs = now;
        notificationManager.notify(NOTIFICATION_ID, buildNotification(steps));
    }

    private Notification buildNotification(int steps) {
        UserPrefs prefs = new UserPrefs(this);
        int goal = prefs.getStepGoal();
        double km = FitCalc.stepsToMeters(steps, prefs.getHeightCm()) / 1000.0;
        long cal = Math.round(FitCalc.stepCalories(steps, prefs.getWeightKg()));

        Intent open = new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_walk)
                .setColor(ContextCompat.getColor(this, R.color.primary))
                .setContentTitle(String.format(Locale.getDefault(), "%,d steps today", steps))
                .setContentText(String.format(Locale.getDefault(),
                        "%.2f km · %d Cal · Goal %,d", km, cal, goal))
                .setProgress(goal, Math.min(steps, goal), false)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();
    }
}
