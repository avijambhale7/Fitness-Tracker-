package com.example.fittracker;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.fittracker.data.AuthManager;
import com.example.fittracker.data.SupabaseClient;
import com.example.fittracker.data.SyncManager;
import com.example.fittracker.data.UserPrefs;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Sign in / create account screen (Supabase Auth). Shown whenever nobody is signed in. */
public class LoginActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private AuthManager auth;
    private boolean registerMode = false;
    private boolean busy = false;

    private View nameBox, confirmBox;
    private EditText etName, etEmail, etPassword, etConfirm;
    private TextView tvTitle, tvSubtitle, tvSwitch;
    private MaterialButton btnSubmit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = new AuthManager(this);
        if (auth.isLoggedIn()) {
            openMain();
            return;
        }
        setContentView(R.layout.activity_login);

        nameBox = findViewById(R.id.box_name);
        confirmBox = findViewById(R.id.box_confirm);
        etName = findViewById(R.id.et_login_name);
        etEmail = findViewById(R.id.et_login_email);
        etPassword = findViewById(R.id.et_login_password);
        etConfirm = findViewById(R.id.et_login_confirm);
        tvTitle = findViewById(R.id.tv_login_title);
        tvSubtitle = findViewById(R.id.tv_login_subtitle);
        tvSwitch = findViewById(R.id.tv_switch_mode);
        btnSubmit = findViewById(R.id.btn_submit);

        btnSubmit.setOnClickListener(v -> submit());
        tvSwitch.setOnClickListener(v -> {
            if (busy) return;
            registerMode = !registerMode;
            updateMode();
        });
        if (savedInstanceState != null) registerMode = savedInstanceState.getBoolean("register");
        updateMode();

        if (!SupabaseClient.isConfigured()) {
            showError("Supabase is not set up in this build. Add supabase.url and "
                    + "supabase.anonKey to local.properties, then rebuild the app.");
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdown();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("register", registerMode);
    }

    private void updateMode() {
        nameBox.setVisibility(registerMode ? View.VISIBLE : View.GONE);
        confirmBox.setVisibility(registerMode ? View.VISIBLE : View.GONE);
        tvTitle.setText(registerMode ? "Create account" : "Welcome back");
        tvSubtitle.setText(registerMode ? "Sign up to start tracking your fitness"
                : "Log in to continue to FitTracker");
        btnSubmit.setText(busy ? "Please wait…" : registerMode ? "Sign up" : "Log in");
        btnSubmit.setEnabled(!busy);
        tvSwitch.setText(registerMode ? "Already have an account? Log in"
                : "New here? Create an account");
    }

    private void submit() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();
        String name = etName.getText().toString().trim();

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Enter a valid email");
            return;
        }
        if (password.length() < 6) {
            etPassword.setError("At least 6 characters");
            return;
        }
        if (registerMode) {
            if (TextUtils.isEmpty(name)) {
                etName.setError("Enter your name");
                return;
            }
            if (!password.equals(etConfirm.getText().toString())) {
                etConfirm.setError("Passwords do not match");
                return;
            }
        }

        boolean signUp = registerMode;
        setBusy(true);
        executor.execute(() -> {
            try {
                if (signUp) {
                    if (!auth.signUp(email, password, name)) {
                        runOnUiThread(() -> onConfirmEmailNeeded(email));
                        return;
                    }
                    UserPrefs prefs = new UserPrefs(this);
                    prefs.save(name, prefs.getAge(), prefs.getHeightCm(), prefs.getWeightKg(),
                            prefs.getStepGoal(), prefs.getHeartGoal());
                } else {
                    auth.signIn(email, password);
                }
                try {
                    SyncManager.get(this).syncAfterLogin();
                } catch (IOException e) {
                    auth.signOut(); // don't open the app with half-downloaded data
                    throw e;
                }
                runOnUiThread(this::openMain);
            } catch (IOException e) {
                runOnUiThread(() -> {
                    setBusy(false);
                    showError(friendlyMessage(e));
                });
            }
        });
    }

    private void onConfirmEmailNeeded(String email) {
        setBusy(false);
        registerMode = false;
        updateMode();
        new MaterialAlertDialogBuilder(this)
                .setTitle("Confirm your email")
                .setMessage("We sent a link to " + email + ". Open it to confirm your account, "
                        + "then come back and log in.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void setBusy(boolean b) {
        busy = b;
        updateMode();
    }

    private void showError(String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(registerMode ? "Could not sign up" : "Could not log in")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private static String friendlyMessage(IOException e) {
        if (e instanceof UnknownHostException) {
            return "No internet connection. Check your connection and try again.";
        }
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.contains("Invalid login credentials")) return "Wrong email or password.";
        if (msg.contains("Email not confirmed")) {
            return "Your email is not confirmed yet. Open the link we emailed you, then log in.";
        }
        if (msg.contains("already registered")) {
            return "An account with this email already exists. Log in instead.";
        }
        return msg.isEmpty() ? "Something went wrong. Please try again." : msg;
    }

    private void openMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
