package com.speechplus.tts;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Switch;

public class SettingsDisplayActivity extends Activity {
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_display);

        prefs = getSharedPreferences("SpeechPlusPrefs", Context.MODE_PRIVATE);

        // Find Switches
        Switch switchDarkMode = findViewById(R.id.switchDarkMode);
        Switch switchKeepScreenOn = findViewById(R.id.switchKeepScreenOn);

        // Load Saved Settings (Default is true/ON)
        switchDarkMode.setChecked(prefs.getBoolean("display_dark_mode", true));
        switchKeepScreenOn.setChecked(prefs.getBoolean("display_keep_screen_on", true));

        // Save Settings when changed
        switchDarkMode.setOnCheckedChangeListener((btn, isChecked) -> prefs.edit().putBoolean("display_dark_mode", isChecked).apply());
        switchKeepScreenOn.setOnCheckedChangeListener((btn, isChecked) -> prefs.edit().putBoolean("display_keep_screen_on", isChecked).apply());

        // Back Button
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }
}
