package com.example.fittracker.ui;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.fittracker.R;
import com.example.fittracker.data.DatabaseHelper;
import com.example.fittracker.data.UserPrefs;
import com.example.fittracker.sensor.StepCounterService;
import com.example.fittracker.sensor.StepTracker;
import com.example.fittracker.util.BatteryHelper;
import com.example.fittracker.util.FitCalc;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

public class ProfileFragment extends Fragment {

    private EditText etName, etAge, etHeight, etWeight, etStepGoal, etHeartGoal;
    private TextView tvAvatar, tvName, tvBmi, tvBmiCategory, tvBackground, tvBodySummary;
    private MaterialButton btnBackground;
    private UserPrefs prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        prefs = new UserPrefs(requireContext());
        etName = v.findViewById(R.id.et_name);
        etAge = v.findViewById(R.id.et_age);
        etHeight = v.findViewById(R.id.et_height);
        etWeight = v.findViewById(R.id.et_weight);
        etStepGoal = v.findViewById(R.id.et_step_goal);
        etHeartGoal = v.findViewById(R.id.et_heart_goal);
        tvAvatar = v.findViewById(R.id.tv_avatar);
        tvName = v.findViewById(R.id.tv_name);
        tvBmi = v.findViewById(R.id.tv_bmi);
        tvBmiCategory = v.findViewById(R.id.tv_bmi_category);
        tvBodySummary = v.findViewById(R.id.tv_body_summary);

        etName.setText(prefs.getName());
        etAge.setText(String.valueOf(prefs.getAge()));
        etHeight.setText(format(prefs.getHeightCm()));
        etWeight.setText(format(prefs.getWeightKg()));
        etStepGoal.setText(String.valueOf(prefs.getStepGoal()));
        etHeartGoal.setText(String.valueOf(prefs.getHeartGoal()));
        updateHeader();

        tvBackground = v.findViewById(R.id.tv_background_status);
        btnBackground = v.findViewById(R.id.btn_background);
        btnBackground.setOnClickListener(x -> {
            if (!StepTracker.hasPermission(requireContext())) {
                BatteryHelper.openAppSettings(requireContext());
            } else {
                BatteryHelper.requestUnrestricted(requireContext());
            }
        });

        v.findViewById(R.id.btn_save).setOnClickListener(x -> save());
        v.findViewById(R.id.btn_clear).setOnClickListener(x -> confirmClear());
    }

    @Override
    public void onResume() {
        super.onResume();
        updateBackgroundStatus();
    }

    private void updateBackgroundStatus() {
        if (!StepTracker.hasPermission(requireContext())) {
            tvBackground.setText("❌ Physical activity permission is off, so steps are not counted.");
            btnBackground.setText("Open app settings");
            btnBackground.setVisibility(View.VISIBLE);
        } else if (!BatteryHelper.isUnrestricted(requireContext())) {
            tvBackground.setText("⚠ Battery optimization is on. Your phone may stop step counting "
                    + "when the app is closed.");
            btnBackground.setText("Allow background use");
            btnBackground.setVisibility(View.VISIBLE);
        } else {
            tvBackground.setText("✅ Counting steps all day, even when the app is closed.");
            btnBackground.setVisibility(View.GONE);
        }
    }

    private void save() {
        String name = etName.getText().toString().trim();
        int age = parseInt(etAge);
        float height = parseFloat(etHeight);
        float weight = parseFloat(etWeight);
        int stepGoal = parseInt(etStepGoal);
        int heartGoal = parseInt(etHeartGoal);

        if (TextUtils.isEmpty(name)) {
            etName.setError("Enter your name");
            return;
        }
        if (age < 5 || age > 120) {
            etAge.setError("Enter a valid age");
            return;
        }
        if (height < 50 || height > 250) {
            etHeight.setError("Height in cm (50-250)");
            return;
        }
        if (weight < 20 || weight > 300) {
            etWeight.setError("Weight in kg (20-300)");
            return;
        }
        if (stepGoal < 100) {
            etStepGoal.setError("Minimum 100 steps");
            return;
        }
        if (heartGoal < 1) {
            etHeartGoal.setError("Minimum 1 point");
            return;
        }

        prefs.save(name, age, height, weight, stepGoal, heartGoal);
        updateHeader();
        StepCounterService.start(requireContext()); // redraws notification with new goal
        Toast.makeText(requireContext(), "Profile saved", Toast.LENGTH_SHORT).show();
    }

    private void updateHeader() {
        String name = prefs.getName();
        tvName.setText(name);
        tvAvatar.setText(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.getDefault()));
        tvBodySummary.setText(String.format(Locale.getDefault(), "%d yrs  ·  %s cm  ·  %s kg",
                prefs.getAge(), format(prefs.getHeightCm()), format(prefs.getWeightKg())));
        double bmi = FitCalc.bmi(prefs.getWeightKg(), prefs.getHeightCm());
        tvBmi.setText(String.format(Locale.getDefault(), "%.1f", bmi));
        tvBmiCategory.setText(FitCalc.bmiCategory(bmi));

        // Colour-coded pill: blue under, green healthy, orange over, red obese
        int color = bmi < 18.5 ? 0xFF38BDF8 : bmi < 25 ? 0xFF22C7A0 : bmi < 30 ? 0xFFFF9F43 : 0xFFE5484D;
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(color);
        pill.setCornerRadius(100 * getResources().getDisplayMetrics().density);
        tvBmiCategory.setBackground(pill);
    }

    private void confirmClear() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Clear all data?")
                .setMessage("This deletes every workout and all step history. This cannot be undone.")
                .setPositiveButton("Clear", (d, w) -> {
                    DatabaseHelper.get(requireContext()).clearAll();
                    StepTracker.reset(requireContext());
                    Toast.makeText(requireContext(), "All data cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static String format(float f) {
        return f == Math.round(f) ? String.valueOf(Math.round(f)) : String.valueOf(f);
    }

    private static int parseInt(EditText et) {
        try {
            return Integer.parseInt(et.getText().toString().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static float parseFloat(EditText et) {
        try {
            return Float.parseFloat(et.getText().toString().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
