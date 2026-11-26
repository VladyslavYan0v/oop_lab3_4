package com.example.mazeapp;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

public class MainActivity extends AppCompatActivity {

    private MazeView mazeView;
    private TextView statusText;
    private TextView timerText;
    private TextView recordText;
    private Button btnDifficulty;
    private Button btnFog;
    private Button btnHint;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private int secondsElapsed = 0;
    private boolean isTimerRunning = false;
    private SharedPreferences prefs;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isTimerRunning) {
                secondsElapsed++;
                updateTimerUI();
                timerHandler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        AppLogger.getLogger().info("MainActivity started");

        prefs = getSharedPreferences("MazeRecords", MODE_PRIVATE);

        mazeView = findViewById(R.id.mazeView);
        statusText = findViewById(R.id.statusText);
        timerText = findViewById(R.id.timerText);
        recordText = findViewById(R.id.recordText);
        btnDifficulty = findViewById(R.id.btnDifficulty);
        btnFog = findViewById(R.id.btnFog);
        btnHint = findViewById(R.id.btnHint);

        mazeView.setOnGameUpdateListener((moves, won) -> {
            statusText.setText(getString(R.string.moves_counter, moves));
            if (moves == 1 && !isTimerRunning) {
                startTimer();
            }
            if (won) {
                stopTimer();
                updateHintButtonUI();
                boolean isNewRecord = checkAndSaveRecord(secondsElapsed);
                updateRecordUI();
                showWinDialog(moves, secondsElapsed, isNewRecord);
            }
        });

        findViewById(R.id.btnUp).setOnClickListener(v -> mazeView.movePlayer(MazeView.Direction.UP));
        findViewById(R.id.btnDown).setOnClickListener(v -> mazeView.movePlayer(MazeView.Direction.DOWN));
        findViewById(R.id.btnLeft).setOnClickListener(v -> mazeView.movePlayer(MazeView.Direction.LEFT));
        findViewById(R.id.btnRight).setOnClickListener(v -> mazeView.movePlayer(MazeView.Direction.RIGHT));

        findViewById(R.id.btnNewGame).setOnClickListener(v -> resetGame());
        findViewById(R.id.btnSwitchLang).setOnClickListener(v -> toggleLanguage());

        btnHint.setOnClickListener(v -> {
            mazeView.toggleHint();
            updateHintButtonUI();
        });

        btnDifficulty.setOnClickListener(v -> cycleDifficulty());
        btnFog.setOnClickListener(v -> {
            mazeView.toggleFogOfWar();
            updateFogButtonUI();
        });

        // Якщо Activity перестворилась (наприклад, після зміни мови) — відновлюємо гру
        if (savedInstanceState != null) {
            mazeView.restoreState(savedInstanceState);
            secondsElapsed = savedInstanceState.getInt("timer_sec", 0);
            isTimerRunning = savedInstanceState.getBoolean("timer_running", false);

            statusText.setText(getString(R.string.moves_counter, mazeView.getMovesCount()));
            updateTimerUI();
            updateRecordUI();
            updateDifficultyUI();
            updateFogButtonUI();
            updateHintButtonUI();

            if (isTimerRunning) {
                timerHandler.postDelayed(timerRunnable, 1000);
            }
        } else {
            updateDifficultyUI();
            updateFogButtonUI();
            updateHintButtonUI();
            resetGame();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mazeView.saveState(outState);
        outState.putInt("timer_sec", secondsElapsed);
        outState.putBoolean("timer_running", isTimerRunning);
    }

    private void resetGame() {
        stopTimer();
        secondsElapsed = 0;
        updateTimerUI();
        updateRecordUI();
        statusText.setText(getString(R.string.moves_counter, 0));
        mazeView.generateNewMaze();
        updateHintButtonUI();
    }

    private void startTimer() {
        isTimerRunning = true;
        timerHandler.postDelayed(timerRunnable, 1000);
    }

    private void stopTimer() {
        isTimerRunning = false;
        timerHandler.removeCallbacks(timerRunnable);
    }

    private void updateTimerUI() {
        int mins = secondsElapsed / 60;
        int secs = secondsElapsed % 60;
        timerText.setText(getString(R.string.time_counter, mins, secs));
    }

    private boolean checkAndSaveRecord(int timeSec) {
        String key = "best_time_" + mazeView.getDifficulty().name();
        int currentBest = prefs.getInt(key, -1);
        if (currentBest == -1 || timeSec < currentBest) {
            prefs.edit().putInt(key, timeSec).apply();
            AppLogger.getLogger().info("New record for " + mazeView.getDifficulty().name() + ": " + timeSec + "s");
            return true;
        }
        return false;
    }

    private void updateRecordUI() {
        String key = "best_time_" + mazeView.getDifficulty().name();
        int best = prefs.getInt(key, -1);
        if (best == -1) {
            recordText.setText(getString(R.string.best_time_none));
        } else {
            recordText.setText(getString(R.string.best_time, best / 60, best % 60));
        }
    }

    private void cycleDifficulty() {
        MazeView.Difficulty current = mazeView.getDifficulty();
        MazeView.Difficulty next;
        if (current == MazeView.Difficulty.EASY) {
            next = MazeView.Difficulty.MEDIUM;
        } else if (current == MazeView.Difficulty.MEDIUM) {
            next = MazeView.Difficulty.HARD;
        } else {
            next = MazeView.Difficulty.EASY;
        }
        mazeView.setDifficulty(next);
        updateDifficultyUI();
        resetGame();
    }

    private void updateDifficultyUI() {
        int resId;
        switch (mazeView.getDifficulty()) {
            case MEDIUM: resId = R.string.diff_medium; break;
            case HARD:   resId = R.string.diff_hard;   break;
            default:     resId = R.string.diff_easy;   break;
        }
        btnDifficulty.setText(resId);
    }

    private void updateFogButtonUI() {
        btnFog.setText(mazeView.isFogOfWar() ? R.string.fog_on : R.string.fog_off);
    }

    private void updateHintButtonUI() {
        btnHint.setAlpha(mazeView.isHintVisible() ? 1.0f : 0.78f);
    }

    private void toggleLanguage() {
        LocaleListCompat currentLocales = AppCompatDelegate.getApplicationLocales();
        String nextLang = currentLocales.toLanguageTags().startsWith("uk") ? "en" : "uk";
        AppLogger.getLogger().info("Switching language to: " + nextLang);
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(nextLang));
    }

    private void showWinDialog(int moves, int timeSec, boolean isNewRecord) {
        String message = getString(R.string.win_message_adv, moves, timeSec / 60, timeSec % 60);
        if (isNewRecord) {
            message += "\n\n" + getString(R.string.new_record_badge);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.win_title)
                .setMessage(message)
                .setPositiveButton(R.string.btn_new_game, (dialog, which) -> resetGame())
                .setCancelable(false)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timerHandler.removeCallbacks(timerRunnable);
    }
}