package com.example.fittracker;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.fittracker.data.AuthManager;
import com.example.fittracker.data.DatabaseHelper;
import com.example.fittracker.data.SyncManager;
import com.example.fittracker.data.UserPrefs;
import com.example.fittracker.model.ActivityType;
import com.example.fittracker.model.Workout;
import com.example.fittracker.sensor.StepCounterService;
import com.example.fittracker.sensor.StepTracker;
import com.example.fittracker.util.BatteryHelper;
import com.example.fittracker.ui.HomeFragment;
import com.example.fittracker.ui.JournalFragment;
import com.example.fittracker.ui.ProfileFragment;
import com.example.fittracker.ui.Refreshable;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PERMISSIONS = 100;

    private BottomNavigationView bottomNav;
    private ExtendedFloatingActionButton fab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!new AuthManager(this).isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottom_nav);
        fab = findViewById(R.id.fab_add);

        bottomNav.setOnItemSelectedListener(item -> {
            showScreen(item.getItemId());
            return true;
        });
        fab.setOnClickListener(v -> showAddOptions());

        if (savedInstanceState == null) {
            showScreen(R.id.nav_home);
        }
        requestPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Make sure background counting is running (no-op if it already is)
        StepCounterService.start(this);
        SyncManager.get(this).retryIfNeeded();
    }

    @Override
    protected void onStop() {
        super.onStop();
        SyncManager.get(this).flushSteps();
    }

    private void showScreen(int itemId) {
        Fragment fragment;
        if (itemId == R.id.nav_journal) {
            fragment = new JournalFragment();
        } else if (itemId == R.id.nav_profile) {
            fragment = new ProfileFragment();
        } else {
            fragment = new HomeFragment();
        }
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
        if (itemId == R.id.nav_profile) fab.hide();
        else fab.show();
    }

    private void refreshCurrent() {
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (f instanceof Refreshable) ((Refreshable) f).refresh();
    }

    private void showAddOptions() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.sheet_add, null);
        view.findViewById(R.id.opt_track).setOnClickListener(v -> {
            sheet.dismiss();
            startActivity(new Intent(this, WorkoutActivity.class));
        });
        view.findViewById(R.id.opt_manual).setOnClickListener(v -> {
            sheet.dismiss();
            showManualEntryDialog();
        });
        sheet.setContentView(view);
        sheet.show();
    }

    private void showManualEntryDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_add_activity, null);
        Spinner spinner = view.findViewById(R.id.spinner_type);
        EditText etMinutes = view.findViewById(R.id.et_minutes);
        EditText etDistance = view.findViewById(R.id.et_distance);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, ActivityType.labels());
        spinner.setAdapter(adapter);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Add activity")
                .setView(view)
                .setPositiveButton("Save", (d, which) -> {
                    int minutes;
                    double km;
                    try {
                        minutes = Integer.parseInt(etMinutes.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        minutes = 0;
                    }
                    try {
                        km = Double.parseDouble(etDistance.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        km = 0;
                    }
                    if (minutes <= 0 || minutes > 1440) {
                        Toast.makeText(this, "Enter a duration between 1 and 1440 minutes",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    ActivityType type = ActivityType.values()[spinner.getSelectedItemPosition()];
                    long durationSec = minutes * 60L;
                    long start = System.currentTimeMillis() - durationSec * 1000;
                    Workout w = Workout.create(type, start, durationSec, km * 1000, 0,
                            new UserPrefs(this).getWeightKg());
                    DatabaseHelper.get(this).insertWorkout(w);
                    Toast.makeText(this, type.label + " added: +" + w.heartPoints + " Heart Pts",
                            Toast.LENGTH_SHORT).show();
                    refreshCurrent();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Asks for step + notification permissions (Android 10+ / 13+). */
    private void requestPermissions() {
        List<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !StepTracker.hasPermission(this)) {
            needed.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), REQ_PERMISSIONS);
        } else {
            askBatteryOptimizationOnce();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_PERMISSIONS) return;
        if (StepTracker.hasPermission(this)) {
            StepCounterService.start(this);
            showScreen(bottomNav.getSelectedItemId());
            askBatteryOptimizationOnce();
        } else {
            Toast.makeText(this, "Allow \"Physical activity\" permission so FitTracker can count your steps",
                    Toast.LENGTH_LONG).show();
        }
    }

    /** Phones may kill background apps to save battery; ask once to be excluded, like fitness apps do. */
    private void askBatteryOptimizationOnce() {
        if (!StepTracker.hasPermission(this) || BatteryHelper.isUnrestricted(this)) return;
        SharedPreferences sp = getSharedPreferences("app_state", MODE_PRIVATE);
        if (sp.getBoolean("asked_battery", false)) return;
        sp.edit().putBoolean("asked_battery", true).apply();
        new MaterialAlertDialogBuilder(this)
                .setTitle("Count steps all day")
                .setMessage("To keep counting your steps when FitTracker is closed, allow it to run "
                        + "in the background without battery restrictions.")
                .setPositiveButton("Allow", (d, w) -> BatteryHelper.requestUnrestricted(this))
                .setNegativeButton("Not now", null)
                .show();
    }
}
