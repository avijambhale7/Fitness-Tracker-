package com.example.fittracker.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fittracker.R;
import com.example.fittracker.data.DatabaseHelper;
import com.example.fittracker.model.Workout;
import com.example.fittracker.util.DateUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.Locale;

/** History of all workouts, newest first. Long-press an entry to delete it. */
public class JournalFragment extends Fragment implements Refreshable {

    private WorkoutAdapter adapter;
    private TextView tvEmpty, tvSumWorkouts, tvSumMinutes, tvSumCalories, tvSumHeart;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_journal, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        tvEmpty = v.findViewById(R.id.tv_empty);
        tvSumWorkouts = v.findViewById(R.id.tv_sum_workouts);
        tvSumMinutes = v.findViewById(R.id.tv_sum_minutes);
        tvSumCalories = v.findViewById(R.id.tv_sum_calories);
        tvSumHeart = v.findViewById(R.id.tv_sum_heart);
        RecyclerView rv = v.findViewById(R.id.rv_journal);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new WorkoutAdapter(this::confirmDelete);
        rv.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void refresh() {
        if (adapter == null) return;
        DatabaseHelper db = DatabaseHelper.get(requireContext());
        List<Workout> all = db.getAllWorkouts();
        adapter.setItems(all);
        tvEmpty.setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);

        long weekStart = DateUtil.startOfDay(DateUtil.daysAgo(6));
        List<Workout> week = db.getWorkoutsBetween(weekStart, Long.MAX_VALUE);
        long seconds = 0;
        double calories = 0;
        int heart = 0;
        for (Workout w : week) {
            seconds += w.durationSec;
            calories += w.calories;
            heart += w.heartPoints;
        }
        tvSumWorkouts.setText(String.valueOf(week.size()));
        tvSumMinutes.setText(String.valueOf(seconds / 60));
        tvSumCalories.setText(String.format(Locale.getDefault(), "%,d", Math.round(calories)));
        tvSumHeart.setText(String.valueOf(heart));
    }

    private void confirmDelete(Workout w) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete activity?")
                .setMessage(w.type.label + " on " + DateUtil.dateTime(w.startTime))
                .setPositiveButton("Delete", (d, which) -> {
                    DatabaseHelper.get(requireContext()).deleteWorkout(w.id);
                    Toast.makeText(requireContext(), "Activity deleted", Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
