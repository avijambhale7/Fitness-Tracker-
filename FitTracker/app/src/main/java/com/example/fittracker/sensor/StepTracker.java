package com.example.fittracker.sensor;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import com.example.fittracker.data.DatabaseHelper;
import com.example.fittracker.util.DateUtil;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Counts today's steps. Only {@link StepCounterService} creates an instance of this
 * class, so steps are counted in the background even when the app is closed.
 * Screens call {@link #addObserver} to get live updates and {@link #readTodaySteps}
 * to read the current total.
 *
 * Uses the hardware TYPE_STEP_COUNTER sensor when available (counts in a low-power chip,
 * total since boot). Devices/emulators without it fall back to an accelerometer
 * peak detector.
 */
public class StepTracker implements SensorEventListener {

    public interface Listener {
        void onStepsUpdated(int todaySteps);
    }

    private static final String PREFS = "step_tracker";
    private static final String K_DAY = "day";
    private static final String K_TODAY = "today_steps";
    private static final String K_LAST_RAW = "last_raw";

    /** Screens currently showing live steps. Sensor callbacks arrive on the main thread. */
    private static final List<Listener> observers = new CopyOnWriteArrayList<>();

    private final Context appContext;
    private final SensorManager sensorManager;
    private final Sensor stepCounter;
    private final Sensor accelerometer;
    private final SharedPreferences sp;
    private final DatabaseHelper db;
    private final Listener listener;

    // Accelerometer step detection state
    private double gravity = SensorManager.GRAVITY_EARTH;
    private boolean aboveThreshold;
    private long lastStepMs;

    public StepTracker(Context context, Listener listener) {
        appContext = context.getApplicationContext();
        this.listener = listener;
        sensorManager = (SensorManager) appContext.getSystemService(Context.SENSOR_SERVICE);
        stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sp = prefs(appContext);
        db = DatabaseHelper.get(appContext);
    }

    // ---------- Static helpers used by the UI ----------

    public static void addObserver(Listener l) {
        observers.add(l);
    }

    public static void removeObserver(Listener l) {
        observers.remove(l);
    }

    public static boolean hasPermission(Context c) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || ContextCompat.checkSelfPermission(c, Manifest.permission.ACTIVITY_RECOGNITION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static String todayKey() {
        return DateUtil.dayKey(System.currentTimeMillis());
    }

    /** Today's steps without touching the sensor. */
    public static int readTodaySteps(Context c) {
        String today = todayKey();
        SharedPreferences sp = prefs(c);
        return today.equals(sp.getString(K_DAY, ""))
                ? sp.getInt(K_TODAY, 0)
                : DatabaseHelper.get(c).getSteps(today);
    }

    public static String getStatus(Context c) {
        if (!hasPermission(c)) {
            return "Step counting is off. Tap here and allow \"Physical activity\" permission.";
        }
        SensorManager sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        if (sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null) {
            return "Counting steps in the background with the step counter sensor";
        }
        if (sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            return "No step sensor found. Counting steps with the accelerometer.";
        }
        return "No step sensor available on this device";
    }

    /** Forgets today's running total (used by "Clear all data"). */
    public static void reset(Context c) {
        prefs(c).edit().remove(K_DAY).remove(K_TODAY).apply();
    }

    /**
     * Also forgets the last sensor reading (used on logout), so steps walked while logged
     * out are not added to the next account that logs in.
     */
    public static void resetAll(Context c) {
        prefs(c).edit().clear().apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---------- Sensor handling (service only) ----------

    public void start() {
        if (stepCounter != null && hasPermission(appContext)) {
            sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL);
        } else if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    public void stop() {
        sensorManager.unregisterListener(this);
    }

    public int getTodaySteps() {
        ensureToday();
        return sp.getInt(K_TODAY, 0);
    }

    /** Starts a fresh total when the date changes. */
    private void ensureToday() {
        String today = DateUtil.dayKey(System.currentTimeMillis());
        if (!today.equals(sp.getString(K_DAY, ""))) {
            sp.edit().putString(K_DAY, today).putInt(K_TODAY, db.getSteps(today)).apply();
        }
    }

    private void addSteps(int n) {
        ensureToday();
        int total = sp.getInt(K_TODAY, 0) + n;
        sp.edit().putInt(K_TODAY, total).apply();
        db.saveSteps(sp.getString(K_DAY, ""), total);
        if (listener != null) listener.onStepsUpdated(total);
        for (Listener l : observers) l.onStepsUpdated(total);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            // Value is the total since last reboot; we add the difference since last reading
            int raw = (int) event.values[0];
            int last = sp.getInt(K_LAST_RAW, -1);
            int delta;
            if (last < 0) delta = 0;            // first reading ever
            else if (raw >= last) delta = raw - last;
            else delta = raw;                   // device rebooted, counter restarted
            sp.edit().putInt(K_LAST_RAW, raw).apply();
            if (delta > 0) addSteps(delta);
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0], y = event.values[1], z = event.values[2];
            double magnitude = Math.sqrt(x * x + y * y + z * z);
            gravity = 0.9 * gravity + 0.1 * magnitude;   // low-pass filter
            double dynamic = magnitude - gravity;
            long now = System.currentTimeMillis();
            if (!aboveThreshold && dynamic > 1.8 && now - lastStepMs > 300) {
                aboveThreshold = true;
                lastStepMs = now;
                addSteps(1);
            } else if (aboveThreshold && dynamic < 0.5) {
                aboveThreshold = false;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
