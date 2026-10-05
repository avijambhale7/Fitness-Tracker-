package com.example.fittracker;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.fittracker.data.DatabaseHelper;
import com.example.fittracker.data.UserPrefs;
import com.example.fittracker.model.ActivityType;
import com.example.fittracker.model.Workout;
import com.example.fittracker.sensor.StepCounterService;
import com.example.fittracker.sensor.StepTracker;
import com.example.fittracker.util.DateUtil;
import com.example.fittracker.util.FitCalc;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

/** Live workout tracking: stopwatch, live calories, Heart Points, steps and distance. */
public class WorkoutActivity extends AppCompatActivity implements StepTracker.Listener {

    private static final long MIN_WORKOUT_SEC = 10;

    private ChipGroup chipGroup;
    private TextView tvType, tvTypeEmoji, tvTimer, tvCalories, tvHeart, tvDistance, tvSteps, tvStatus, tvState;
    private View hero;
    private MaterialButton btnStart, btnFinish;

    private ActivityType type = ActivityType.WALKING;
    private boolean started;
    private boolean running;
    private long accumulatedMs;
    private long segmentStart;
    private long startWallTime;
    private int workoutSteps;
    private int lastTodaySteps;

    private UserPrefs prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateStats();
            if (running) handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_workout);
        prefs = new UserPrefs(this);

        chipGroup = findViewById(R.id.chip_group);
        tvType = findViewById(R.id.tv_type);
        tvTimer = findViewById(R.id.tv_timer);
        tvCalories = findViewById(R.id.tv_w_calories);
        tvHeart = findViewById(R.id.tv_w_heart);
        tvDistance = findViewById(R.id.tv_w_distance);
        tvSteps = findViewById(R.id.tv_w_steps);
        tvStatus = findViewById(R.id.tv_w_status);
        tvState = findViewById(R.id.tv_w_state);
        tvTypeEmoji = findViewById(R.id.tv_type_emoji);
        hero = findViewById(R.id.hero_workout);
        btnStart = findViewById(R.id.btn_start);
        btnFinish = findViewById(R.id.btn_finish);

        buildTypeChips();

        // Steps come from the background StepCounterService
        StepCounterService.start(this);
        StepTracker.addObserver(this);
        lastTodaySteps = StepTracker.readTodaySteps(this);
        tvStatus.setText(StepTracker.getStatus(this));

        btnStart.setOnClickListener(v -> toggle());
        btnFinish.setOnClickListener(v -> finishWorkout());
        findViewById(R.id.btn_close).setOnClickListener(v -> confirmExit());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmExit();
            }
        });
        applyTheme();
        updateState();
        updateStats();
    }

    private void buildTypeChips() {
        LayoutInflater inflater = getLayoutInflater();
        for (ActivityType t : ActivityType.values()) {
            Chip chip = (Chip) inflater.inflate(R.layout.item_chip, chipGroup, false);
            chip.setId(View.generateViewId());
            chip.setText(t.emoji + " " + t.label);
            chip.setTag(t);
            chipGroup.addView(chip);
            if (t == type) chipGroup.check(chip.getId());
        }
        chipGroup.setOnCheckedStateChangeListener((group, ids) -> {
            if (!ids.isEmpty()) {
                Chip c = group.findViewById(ids.get(0));
                type = (ActivityType) c.getTag();
                applyTheme();
                updateStats();
            }
        });
    }

    private void setChipsEnabled(boolean enabled) {
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            chipGroup.getChildAt(i).setEnabled(enabled);
        }
    }

    private void toggle() {
        if (!started) {
            started = true;
            startWallTime = System.currentTimeMillis();
            setChipsEnabled(false);
            btnFinish.setEnabled(true);
        }
        if (running) {
            accumulatedMs += SystemClock.elapsedRealtime() - segmentStart;
            running = false;
            handler.removeCallbacks(ticker);
            btnStart.setText("Resume");
            btnStart.setIconResource(R.drawable.ic_play);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            running = true;
            segmentStart = SystemClock.elapsedRealtime();
            lastTodaySteps = StepTracker.readTodaySteps(this);
            handler.post(ticker);
            btnStart.setText("Pause");
            btnStart.setIconResource(R.drawable.ic_pause);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        updateState();
        updateStats();
    }

    private long elapsedSec() {
        long ms = accumulatedMs;
        if (running) ms += SystemClock.elapsedRealtime() - segmentStart;
        return ms / 1000;
    }

    private double distanceMeters() {
        if (type.usesSteps) return FitCalc.stepsToMeters(workoutSteps, prefs.getHeightCm());
        return type.avgSpeedKmh * 1000 * elapsedSec() / 3600.0;
    }

    /** Hero card gradient: selected activity colour blending into the brand violet. */
    private void applyTheme() {
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{type.color, ContextCompat.getColor(this, R.color.hero_start)});
        bg.setCornerRadius(28 * getResources().getDisplayMetrics().density);
        hero.setBackground(bg);
        tvTypeEmoji.setText(type.emoji);
        tvType.setText(type.label);
    }

    private void updateState() {
        String state;
        int color;
        if (!started) {
            state = "READY";
            color = R.color.heart_color;
        } else if (running) {
            state = "● TRACKING";
            color = R.color.primary;
        } else {
            state = "PAUSED";
            color = R.color.steps_color;
        }
        tvState.setText(state);
        btnStart.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, color)));
    }

    private void updateStats() {
        long sec = elapsedSec();
        tvTimer.setText(DateUtil.timer(sec));
        tvCalories.setText(String.valueOf(Math.round(FitCalc.workoutCalories(type, prefs.getWeightKg(), sec))));
        tvHeart.setText(String.valueOf(FitCalc.heartPoints(type, sec)));
        tvDistance.setText(String.format(Locale.getDefault(), "%.2f", distanceMeters() / 1000));
        tvSteps.setText(String.valueOf(workoutSteps));
    }

    @Override
    public void onStepsUpdated(int todaySteps) {
        int delta = todaySteps - lastTodaySteps;
        lastTodaySteps = todaySteps;
        if (running && delta > 0) {
            workoutSteps += delta;
            updateStats();
        }
    }

    private void finishWorkout() {
        if (running) toggle();
        long sec = elapsedSec();
        if (sec < MIN_WORKOUT_SEC) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Workout too short")
                    .setMessage("Workouts shorter than " + MIN_WORKOUT_SEC + " seconds are not saved.")
                    .setPositiveButton("Keep going", null)
                    .setNegativeButton("Discard", (d, w) -> finish())
                    .show();
            return;
        }

        Workout w = Workout.create(type, startWallTime, sec, distanceMeters(), workoutSteps,
                prefs.getWeightKg());
        DatabaseHelper.get(this).insertWorkout(w);

        String summary = String.format(Locale.getDefault(),
                "%s %s\n\nDuration: %s\nCalories: %d Cal\nHeart Points: %d\nSteps: %d\nDistance: %.2f km",
                type.emoji, type.label, DateUtil.duration(sec), Math.round(w.calories),
                w.heartPoints, w.steps, w.distanceM / 1000);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Workout saved 🎉")
                .setMessage(summary)
                .setCancelable(false)
                .setPositiveButton("Done", (d, x) -> finish())
                .show();
    }

    private void confirmExit() {
        if (!started) {
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Discard workout?")
                .setMessage("Your current workout will not be saved.")
                .setPositiveButton("Discard", (d, w) -> finish())
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(ticker);
        StepTracker.removeObserver(this);
        super.onDestroy();
    }
}
