package com.example.fittracker.ui;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.fittracker.R;
import com.example.fittracker.data.DatabaseHelper;
import com.example.fittracker.data.DayStats;
import com.example.fittracker.data.UserPrefs;
import com.example.fittracker.model.Workout;
import com.example.fittracker.sensor.StepTracker;
import com.example.fittracker.util.BatteryHelper;
import com.example.fittracker.util.DateUtil;
import com.example.fittracker.views.BarChartView;
import com.example.fittracker.views.RingView;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment implements StepTracker.Listener, Refreshable {

    private static final int MAX_STREAK_DAYS = 365;

    private RingView ring;
    private TextView tvGreeting, tvDate, tvAvatar, tvGoalHeart, tvGoalSteps, tvMotivation;
    private TextView tvCalories, tvDistance, tvMoveMin, tvSensor, tvNoRecent;
    private TextView tvStreak, tvWeekGoals, tvChartTotal;
    private LinearLayout recentContainer, weekDots;
    private BarChartView chart;
    private MaterialButtonToggleGroup chartToggle;

    // Last 7 days (index 6 = today)
    private final float[] weekSteps = new float[7];
    private final float[] weekHeart = new float[7];
    private final String[] weekLabels = new String[7];

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        ring = v.findViewById(R.id.ring);
        tvGreeting = v.findViewById(R.id.tv_greeting);
        tvDate = v.findViewById(R.id.tv_date);
        tvAvatar = v.findViewById(R.id.tv_home_avatar);
        tvGoalHeart = v.findViewById(R.id.tv_goal_heart);
        tvGoalSteps = v.findViewById(R.id.tv_goal_steps);
        tvMotivation = v.findViewById(R.id.tv_motivation);
        tvCalories = v.findViewById(R.id.tv_calories);
        tvDistance = v.findViewById(R.id.tv_distance);
        tvMoveMin = v.findViewById(R.id.tv_move_min);
        tvSensor = v.findViewById(R.id.tv_sensor);
        tvNoRecent = v.findViewById(R.id.tv_no_recent);
        tvStreak = v.findViewById(R.id.tv_streak);
        tvWeekGoals = v.findViewById(R.id.tv_week_goals);
        tvChartTotal = v.findViewById(R.id.tv_chart_total);
        recentContainer = v.findViewById(R.id.ll_recent);
        weekDots = v.findViewById(R.id.ll_week_dots);
        chart = v.findViewById(R.id.chart_week);
        chartToggle = v.findViewById(R.id.toggle_chart);

        chartToggle.addOnButtonCheckedListener((group, id, checked) -> {
            if (checked) showChart(true);
        });
        tvSensor.setOnClickListener(x -> {
            if (!StepTracker.hasPermission(requireContext())) {
                BatteryHelper.openAppSettings(requireContext());
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // Steps are counted by StepCounterService; this screen only listens for updates
        StepTracker.addObserver(this);
        tvSensor.setText(StepTracker.getStatus(requireContext()));
        load(true);
    }

    @Override
    public void onPause() {
        super.onPause();
        StepTracker.removeObserver(this);
    }

    @Override
    public void onStepsUpdated(int todaySteps) {
        if (isResumed()) load(false);
    }

    @Override
    public void refresh() {
        if (isResumed()) load(true);
    }

    private void load(boolean animate) {
        UserPrefs prefs = new UserPrefs(requireContext());
        long now = System.currentTimeMillis();
        // A full year is only needed for the streak, which is recalculated on full reloads
        DayStats[] days = DayStats.lastDays(requireContext(), animate ? MAX_STREAK_DAYS + 1 : 7);
        DayStats today = days[days.length - 1];

        String firstName = prefs.getName().trim().split(" ")[0];
        tvGreeting.setText(greeting() + ", " + firstName);
        tvDate.setText(DateUtil.fullDate(now));
        tvAvatar.setText(firstName.isEmpty() ? "?" : firstName.substring(0, 1).toUpperCase(Locale.getDefault()));

        ring.setData(today.heartPoints, prefs.getHeartGoal(), today.steps, prefs.getStepGoal(), animate);
        tvGoalHeart.setText(String.format(Locale.getDefault(), "♥ %d / %d pts",
                today.heartPoints, prefs.getHeartGoal()));
        tvGoalSteps.setText(String.format(Locale.getDefault(), "👣 %,d / %,d",
                today.steps, prefs.getStepGoal()));
        tvMotivation.setText(motivation(today, prefs));

        tvCalories.setText(String.valueOf(Math.round(today.calories)));
        tvDistance.setText(String.format(Locale.getDefault(), "%.2f", today.distanceKm));
        tvMoveMin.setText(String.valueOf(today.moveMinutes));

        loadWeek(prefs, Arrays.copyOfRange(days, days.length - 7, days.length));
        showChart(animate);
        if (animate) {
            loadStreak(prefs, days);
            loadRecent();
        }
    }

    private static String greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 5) return "Up late";
        if (hour < 12) return "Good morning";
        if (hour < 17) return "Good afternoon";
        return "Good evening";
    }

    private static String motivation(DayStats s, UserPrefs p) {
        int left = p.getStepGoal() - s.steps;
        if (s.steps >= p.getStepGoal() && s.heartPoints >= p.getHeartGoal()) {
            return "Both goals smashed today. Legend! 🏆";
        }
        if (left <= 0) return "Step goal reached! Now earn some Heart Pts 💪";
        if (s.steps == 0) return "Let's get moving. Every step counts! 🚀";
        if (s.steps < p.getStepGoal() / 2) return "Nice start! Keep that momentum going ✨";
        return String.format(Locale.getDefault(), "Only %,d steps to go. You've got this! 🔥", left);
    }

    /** @param week the last 7 days, oldest first */
    private void loadWeek(UserPrefs prefs, DayStats[] week) {
        for (int i = 0; i < 7; i++) {
            int daysAgo = 6 - i;
            weekSteps[i] = week[i].steps;
            weekHeart[i] = week[i].heartPoints;
            weekLabels[i] = daysAgo == 0 ? "Today" : DateUtil.shortDay(DateUtil.daysAgo(daysAgo));
        }
        drawWeekDots(prefs, week);
    }

    private void showChart(boolean animate) {
        UserPrefs prefs = new UserPrefs(requireContext());
        boolean steps = chartToggle.getCheckedButtonId() != R.id.btn_chart_heart;
        float total = 0;
        for (float f : steps ? weekSteps : weekHeart) total += f;
        if (steps) {
            chart.setData(weekSteps, weekLabels, prefs.getStepGoal(),
                    color(R.color.steps_light), color(R.color.steps_color), animate);
            tvChartTotal.setText(String.format(Locale.getDefault(), "%,d steps", Math.round(total)));
        } else {
            chart.setData(weekHeart, weekLabels, prefs.getHeartGoal(),
                    color(R.color.heart_light), color(R.color.heart_color), animate);
            tvChartTotal.setText(String.format(Locale.getDefault(), "%,d Heart Pts", Math.round(total)));
        }
    }

    /** One circle per day: filled when the step goal was reached. */
    private void drawWeekDots(UserPrefs prefs, DayStats[] week) {
        weekDots.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int met = 0;
        for (int i = 0; i < 7; i++) {
            boolean done = week[i].goalMet(prefs);
            boolean isToday = i == 6;
            if (done) met++;

            LinearLayout col = new LinearLayout(requireContext());
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            col.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView dot = new TextView(requireContext());
            int size = (int) (32 * density);
            dot.setLayoutParams(new LinearLayout.LayoutParams(size, size));
            dot.setGravity(Gravity.CENTER);
            dot.setTextColor(color(R.color.white));
            dot.setTextSize(14);
            dot.setTypeface(Typeface.DEFAULT_BOLD);
            if (done) {
                dot.setBackgroundResource(R.drawable.bg_dot_done);
                dot.setText("✓");
            } else {
                dot.setBackgroundResource(isToday ? R.drawable.bg_dot_today : R.drawable.bg_dot_empty);
            }

            TextView label = new TextView(requireContext());
            label.setText(weekLabels[i]);
            label.setTextSize(11);
            label.setGravity(Gravity.CENTER);
            label.setTextColor(color(isToday ? R.color.primary : R.color.text_secondary));
            if (isToday) label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setPadding(0, (int) (6 * density), 0, 0);

            col.addView(dot);
            col.addView(label);
            weekDots.addView(col);
        }
        tvWeekGoals.setText(met + "/7 goals");
    }

    /** Consecutive days (ending today or yesterday) on which the step or Heart Pts goal was met. */
    private void loadStreak(UserPrefs prefs, DayStats[] days) {
        int last = days.length - 1;
        int streak = days[last].goalMet(prefs) ? 1 : 0;
        for (int i = last - 1; i >= 0 && days[i].goalMet(prefs); i--) streak++;
        if (streak == 0) {
            tvStreak.setText("Start a streak today 🌱");
        } else {
            tvStreak.setText(streak + (streak == 1 ? " day" : " days") + " on fire 🔥");
        }
    }

    private void loadRecent() {
        recentContainer.removeAllViews();
        List<Workout> recent = DatabaseHelper.get(requireContext()).getRecentWorkouts(3);
        tvNoRecent.setVisibility(recent.isEmpty() ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (Workout w : recent) {
            View item = inflater.inflate(R.layout.item_workout, recentContainer, false);
            WorkoutAdapter.bind(item, w);
            recentContainer.addView(item);
        }
    }

    private int color(int res) {
        return ContextCompat.getColor(requireContext(), res);
    }
}
