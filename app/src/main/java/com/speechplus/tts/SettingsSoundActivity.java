package com.speechplus.tts;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Switch;

public class SettingsSoundActivity extends Activity {
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_sound);

        prefs = getSharedPreferences("SpeechPlusPrefs", Context.MODE_PRIVATE);

        // Find Switches
        Switch switchPageTurn = findViewById(R.id.switchPageTurn);
        Switch switchScanProgress = findViewById(R.id.switchScanProgress);
        Switch switchScanComplete = findViewById(R.id.switchScanComplete);
        Switch switchHaptic = findViewById(R.id.switchHaptic);

        // Load Saved Settings (Default is true/ON)
        switchPageTurn.setChecked(prefs.getBoolean("sound_page_turn", true));
        switchScanProgress.setChecked(prefs.getBoolean("sound_scan_progress", true));
        switchScanComplete.setChecked(prefs.getBoolean("sound_scan_complete", true));
        switchHaptic.setChecked(prefs.getBoolean("vibration_haptic", true));

        // Save Settings when changed
        switchPageTurn.setOnCheckedChangeListener((btn, isChecked) -> prefs.edit().putBoolean("sound_page_turn", isChecked).apply());
        switchScanProgress.setOnCheckedChangeListener((btn, isChecked) -> prefs.edit().putBoolean("sound_scan_progress", isChecked).apply());
        switchScanComplete.setOnCheckedChangeListener((btn, isChecked) -> prefs.edit().putBoolean("sound_scan_complete", isChecked).apply());
        switchHaptic.setOnCheckedChangeListener((btn, isChecked) -> prefs.edit().putBoolean("vibration_haptic", isChecked).apply());

        // Back Button
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }
}
