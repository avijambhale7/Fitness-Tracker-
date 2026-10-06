# FitTracker – Fitness & Workout Tracker (Android, Java)

A Google Fit–style fitness app built for the Mobile Application Development micro project.

## Features

| Screen | What it does |
|---|---|
| **Home** | Google Fit–style activity rings: the outer ring shows **Heart Points** and the inner ring shows **Steps**. It also shows today's Calories, Distance and Move Minutes, 7-day bar charts for steps and Heart Points, and your recent activities. |
| **Track workout** (＋ button) | Pick an activity (Walking, Running, Cycling, Yoga, HIIT…). A live stopwatch shows calories, Heart Points, steps and distance, with Start / Pause / Resume / Finish. |
| **Add activity manually** (＋ button) | Log a past workout by type, duration and distance. |
| **Journal** | Full workout history with a 7-day summary. Long-press an entry to delete it. |
| **Profile** | Name, age, height and weight, with a BMI and category. You can edit the daily step and Heart Point goals and clear all data. |

### Background step counting (like Google Fit)
- `StepCounterService` is a **foreground service** (type `health`). It keeps the step sensor registered all day, so steps are counted even when the app is closed or swiped away.
- A silent ongoing notification shows **"X steps today"** with a progress bar, distance and calories. Tapping it opens the app.
- Because the service is always listening, steps are saved to the correct day and the counter resets at midnight.
- `BootReceiver` restarts counting automatically after the **phone reboots** or the app is updated.
- The app asks once to be excluded from **battery optimization** so the phone does not kill the service. The *Profile → Background step counting* card shows the current status.

### How the numbers are calculated
- **Steps**: the phone's hardware step counter sensor (`TYPE_STEP_COUNTER`). If a device has no such sensor, the app falls back to counting steps with the accelerometer.
- **Heart Points**: follow Google Fit's rule. You get 1 point per minute of moderate activity (walking, yoga…) and 2 points per minute of vigorous activity (running, cycling, HIIT…).
- **Calories**: `MET × weight(kg) × hours` for workouts, plus about 0.04 kcal per step.
- **Distance**: steps × stride length, where stride length is 41.5% of your height.
- **BMI**: `weight / height²`.

### Tech used
- Java, Android SDK (min API 24 / Android 7.0)
- SQLite (`SQLiteOpenHelper`) stores workouts and daily steps
- SharedPreferences stores the profile and goals
- Fragments + BottomNavigationView, RecyclerView, Material Design 3 components
- Custom `View`s (Canvas drawing) for the rings and bar charts
- SensorManager for the step counter and accelerometer, plus the runtime permission `ACTIVITY_RECOGNITION`

## Project structure
```
app/src/main/java/com/example/fittracker/
├── LoginActivity.java         log in / sign up (Supabase Auth)
├── MainActivity.java          bottom navigation, + menu, permission request
├── WorkoutActivity.java       live workout tracking (stopwatch)
├── model/  ActivityType, Workout
├── data/   DatabaseHelper (SQLite), UserPrefs, DayStats,
│           SupabaseClient, AuthManager (login session), SyncManager (cloud sync)
├── sensor/ StepCounterService (background), StepTracker (sensor logic), BootReceiver
├── ui/     HomeFragment, JournalFragment, ProfileFragment, WorkoutAdapter
├── views/  RingView, BarChartView (custom drawn)
└── util/   FitCalc (formulas), DateUtil, BatteryHelper
```

## Supabase setup (login + cloud database)
Accounts and data are stored in [Supabase](https://supabase.com). The app keeps a local SQLite
copy so it works offline, and syncs each change to the signed-in user's rows.

1. Create a free project at https://supabase.com/dashboard.
2. Open **SQL Editor → New query**, paste the contents of [`supabase/schema.sql`](supabase/schema.sql) and click **Run**.
   This creates the `profiles`, `workouts` and `daily_steps` tables with Row Level Security
   (each user can only see their own rows).
3. Open **Project Settings → API** and copy the **Project URL** and the **anon / publishable key**
   into `local.properties` (this file is not committed to git):
   ```
   supabase.url=https://YOUR-PROJECT.supabase.co
   supabase.anonKey=YOUR-ANON-KEY
   ```
4. Optional, for testing: **Authentication → Sign In / Providers → Email**, turn off
   **Confirm email** so new accounts can log in immediately without clicking an email link.
5. Rebuild and run the app.

## How to run

### Option A – Android Studio (recommended)
1. Install **Android Studio** (https://developer.android.com/studio).
2. **File → Open…** and select the `FitTracker` folder (the one containing `settings.gradle`).
3. Wait for **Gradle Sync** to finish. The first sync downloads dependencies, so you need internet access.
4. Choose a device:
   - **Real phone (best, because steps work properly):** On the phone, enable *Developer options* (tap *Build number* 7 times) and turn on *USB debugging*. Connect it by USB and allow the prompt.
   - **Emulator:** **Tools → Device Manager → Create Virtual Device** (e.g. Pixel 7, API 34).
5. Press the green **Run ▶** button (Shift+F10).
6. When the app asks, allow the **Physical activity** permission so it can count steps.

> **Testing steps on the emulator:** emulators have no real step sensor, so the app uses the accelerometer. Open the emulator's **⋯ Extended controls → Virtual sensors** and move or shake the device to generate steps. On a real phone, just walk.

### Option B – Command line
```bash
# from the FitTracker folder
gradlew assembleDebug          # builds app/build/outputs/apk/debug/app-debug.apk
gradlew installDebug           # installs on a connected phone/emulator
```
`local.properties` must point to your Android SDK:
`sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk` (Android Studio creates this file automatically).

## Demo flow for presentation
1. Open the app, allow the permission, and show the Home rings.
2. Tap **＋ → Track workout**, pick *Running*, then **Start**. Show the live timer, calories and Heart Points, then tap **Finish & save**.
3. The Home rings and charts update, and the workout appears under *Recent activities*.
4. Tap **＋ → Add activity manually**, then add *Cycling* for 30 minutes.
5. Open **Journal** to see the history, then long-press to delete an entry.
6. Open **Profile**, change your weight and goals, **Save**, and show the BMI update.
