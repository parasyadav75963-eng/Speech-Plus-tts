package com.speechplus.tts;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import java.util.Calendar;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private android.speech.tts.TextToSpeech testTts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        prefs = getSharedPreferences("SpeechPlusPrefs", MODE_PRIVATE);

        // Start our Smart Announcer Service in the background
        startService(new Intent(this, SmartAnnouncerService.class));

        setupTalkBackTabDescriptions();
        setupTestButtons();
    }

    private void setupTalkBackTabDescriptions() {
        setTabDesc("tabHome", R.string.desc_tab_home);
        setTabDesc("tabModes", R.string.desc_tab_modes);
        setTabDesc("tabLanguages", R.string.desc_tab_languages);
        setTabDesc("tabVoices", R.string.desc_tab_voices);
        setTabDesc("tabAdvanced", R.string.desc_tab_advanced);
        setTabDesc("tabMore", R.string.desc_tab_more);
    }

    private void setTabDesc(String idName, int stringRes) {
        int resId = getResources().getIdentifier(idName, "id", getPackageName());
        if (resId != 0) {
            View v = findViewById(resId);
            if (v != null) v.setContentDescription(getString(stringRes));
        }
    }

    private void setupTestButtons() {
        int idTime = getResources().getIdentifier("btnTestTime", "id", getPackageName());
        if (idTime != 0) {
            Button btnTestTime = findViewById(idTime);
            if (btnTestTime != null) {
                btnTestTime.setOnClickListener(v -> testCurrentTime());
            }
        }

        int idBattery = getResources().getIdentifier("btnTestBattery", "id", getPackageName());
        if (idBattery != 0) {
            Button btnTestBattery = findViewById(idBattery);
            if (btnTestBattery != null) {
                btnTestBattery.setOnClickListener(v -> testCurrentBattery());
            }
        }
    }

    private void testCurrentTime() {
        Calendar c = Calendar.getInstance();
        int minute = c.get(Calendar.MINUTE);
        int hour = c.get(Calendar.HOUR);
        if (hour == 0) hour = 12;
        String amPm = c.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM";
        String timeText = "The time is " + hour + " " + (minute == 0 ? "o'clock" : minute) + " " + amPm;
        Toast.makeText(this, timeText, Toast.LENGTH_SHORT).show();
        speakTestText(timeText);
    }

    private void testCurrentBattery() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        int level = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1;
        String batteryText = "Current battery level is " + level + " percent";
        Toast.makeText(this, batteryText, Toast.LENGTH_SHORT).show();
        speakTestText(batteryText);
    }

    private void speakTestText(String text) {
        if (testTts == null) {
            testTts = new android.speech.tts.TextToSpeech(this, status -> {
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    playChimeAndSpeak(text);
                }
            });
        } else {
            playChimeAndSpeak(text);
        }
    }

    private void playChimeAndSpeak(String text) {
        try {
            android.net.Uri soundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION);
            android.media.MediaPlayer mp = android.media.MediaPlayer.create(this, soundUri);
            if (mp != null) {
                mp.setOnCompletionListener(mediaPlayer -> {
                    mediaPlayer.release();
                    testTts.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null);
                });
                mp.start();
            } else {
                testTts.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null);
            }
        } catch (Exception e) {
            testTts.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    protected void onDestroy() {
        if (testTts != null) {
            testTts.stop();
            testTts.shutdown();
        }
        super.onDestroy();
    }
}
