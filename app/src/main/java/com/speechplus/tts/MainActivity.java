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

    
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        prefs = getSharedPreferences("SpeechPlusPrefs", MODE_PRIVATE);

        // Start our 100% working Smart Announcer Service in the background
        startService(new Intent(this, SmartAnnouncerService.class));

        setupTalkBackTabDescriptions();
        setupTestButtons();
    }

    private void setupTalkBackTabDescriptions() {
        // Find Tab Buttons/Views by standard IDs and set their English Descriptions for TalkBack
        View tabHome = findViewById(R.id.tabHome);
        if (tabHome != null) tabHome.setContentDescription(getString(R.string.desc_tab_home));

        View tabModes = findViewById(R.id.tabModes);
        if (tabModes != null) tabModes.setContentDescription(getString(R.string.desc_tab_modes));

        View tabLanguages = findViewById(R.id.tabLanguages);
        if (tabLanguages != null) tabLanguages.setContentDescription(getString(R.string.desc_tab_languages));
        View tabVoices = findViewById(R.id.tabVoices);
        if (tabVoices != null) tabVoices.setContentDescription(getString(R.string.desc_tab_voices));

        View tabAdvanced = findViewById(R.id.tabAdvanced);
        if (tabAdvanced != null) tabAdvanced.setContentDescription(getString(R.string.desc_tab_advanced));

        View tabMore = findViewById(R.id.tabMore);
        if (tabMore != null) tabMore.setContentDescription(getString(R.string.desc_tab_more));
    }

    private void setupTestButtons() {
        Button btnTestTime = findViewById(R.id.btnTestTime);
        if (btnTestTime != null) {
            btnTestTime.setOnClickListener(new View.OnClickListener() {
                
                public void onClick(View v) {
                    testCurrentTime();
                }
            });
        }

        Button btnTestBattery = findViewById(R.id.btnTestBattery);
        if (btnTestBattery != null) {
            btnTestBattery.setOnClickListener(new View.OnClickListener() {
                
                public void onClick(View v) {
                    testCurrentBattery();
                }
            });
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

    private android.speech.tts.TextToSpeech testTts;

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

    
    protected void onDestroy() {
        if (testTts != null) {
            testTts.stop();
            testTts.shutdown();
        }
        super.onDestroy();
    }
}
