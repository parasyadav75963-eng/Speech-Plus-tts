package com.speechplus.tts;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Switch;

public class SettingsTimerBatteryActivity extends Activity {
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_timer_battery);

        prefs = getSharedPreferences("SpeechPlusPrefs", Context.MODE_PRIVATE);

        Switch switchSleepTimer = findViewById(R.id.switchSleepTimer);
        Switch switchBatteryAlert = findViewById(R.id.switchBatteryAlert);
        Switch switchAudioRouting = findViewById(R.id.switchAudioRouting);

        // Load Saved Settings
        switchSleepTimer.setChecked(prefs.getBoolean("timer_sleep", false));
        switchBatteryAlert.setChecked(prefs.getBoolean("battery_alert", true));
        switchAudioRouting.setChecked(prefs.getBoolean("audio_routing_media", false));

        // Save Settings
        switchSleepTimer.setOnCheckedChangeListener((btn, isChecked) -> prefs.edit().putBoolean("timer_sleep", isChecked).apply());
        switchBatteryAlert.setOnCheckedChangeListener((btn, isChecked) -> prefs.edit().putBoolean("battery_alert", isChecked).apply());
        switchAudioRouting.setOnCheckedChangeListener((btn, isChecked) -> prefs.edit().putBoolean("audio_routing_media", isChecked).apply());

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }
}
