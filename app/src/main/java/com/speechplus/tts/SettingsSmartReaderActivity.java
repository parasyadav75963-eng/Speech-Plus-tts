package com.speechplus.tts;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Switch;

public class SettingsSmartReaderActivity extends Activity {
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_smart_reader);

        prefs = getSharedPreferences("SpeechPlusPrefs", Context.MODE_PRIVATE);

        // Find Switches
        Switch switchBackgroundReading = findViewById(R.id.switchBackgroundReading);
        Switch switchAutoStart = findViewById(R.id.switchAutoStart);

        // Load Saved Settings (Default is true/ON)
        switchBackgroundReading.setChecked(prefs.getBoolean("reader_background", true));
        switchAutoStart.setChecked(prefs.getBoolean("reader_autostart", true));

        // Save Settings when changed
        switchBackgroundReading.setOnCheckedChangeListener((btn, isChecked) -> prefs.edit().putBoolean("reader_background", isChecked).apply());
        switchAutoStart.setOnCheckedChangeListener((btn, isChecked) -> prefs.edit().putBoolean("reader_autostart", isChecked).apply());

        // Back Button
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }
}
