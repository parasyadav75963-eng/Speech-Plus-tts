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

        setupTabs();
        setupAllSpinners();
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
        Button btnTestTime = findViewById(getResources().getIdentifier("btnTestTime", "id", getPackageName()));
        if (btnTestTime != null) {
            btnTestTime.setOnClickListener(new View.OnClickListener() {
                
                public void onClick(View v) {
                    testCurrentTime();
                }
            });
        }

        Button btnTestBattery = findViewById(getResources().getIdentifier("btnTestBattery", "id", getPackageName()));
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

    private void setupTabs() {
        final android.view.View layoutHome = findViewById(getResources().getIdentifier("layoutAudioStatus", "id", getPackageName()));
        final android.view.View layoutModes = findViewById(getResources().getIdentifier("radioGroupModes", "id", getPackageName()));
        final android.view.View layoutLanguages = findViewById(getResources().getIdentifier("layoutLanguagesList", "id", getPackageName()));
        
        android.view.View tabHome = findViewById(getResources().getIdentifier("tabHome", "id", getPackageName()));
        android.view.View tabModes = findViewById(getResources().getIdentifier("tabModes", "id", getPackageName()));
        android.view.View tabLanguages = findViewById(getResources().getIdentifier("tabLanguages", "id", getPackageName()));

        if(tabHome != null) tabHome.setOnClickListener(v -> { if(layoutHome != null) layoutHome.setVisibility(android.view.View.VISIBLE); if(layoutModes != null) layoutModes.setVisibility(android.view.View.GONE); if(layoutLanguages != null) layoutLanguages.setVisibility(android.view.View.GONE); });
        if(tabModes != null) tabModes.setOnClickListener(v -> { if(layoutHome != null) layoutHome.setVisibility(android.view.View.GONE); if(layoutModes != null) layoutModes.setVisibility(android.view.View.VISIBLE); if(layoutLanguages != null) layoutLanguages.setVisibility(android.view.View.GONE); });
        if(tabLanguages != null) tabLanguages.setOnClickListener(v -> { if(layoutHome != null) layoutHome.setVisibility(android.view.View.GONE); if(layoutModes != null) layoutModes.setVisibility(android.view.View.GONE); if(layoutLanguages != null) layoutLanguages.setVisibility(android.view.View.VISIBLE); });
    }

    private void setupSpinner(String idName, String[] items) {
        android.widget.Spinner spinner = findViewById(getResources().getIdentifier(idName, "id", getPackageName()));
        if (spinner != null) {
            android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
        }
    }

    private void setupAllSpinners() {
        setupSpinner("spinnerBatteryInterval", new String[]{"10 Minutes", "20 Minutes", "30 Minutes", "1 Hour"});
        setupSpinner("spinnerTimeInterval", new String[]{"15 Minutes", "30 Minutes", "1 Hour"});
        setupSpinner("spinnerTimeFormat", new String[]{"12-Hour AM/PM", "24-Hour Military"});
        setupSpinner("spinnerCallFormat", new String[]{"Caller Name Only", "Number Only", "Name and Number"});
        setupSpinner("spinnerCallRepeat", new String[]{"Once", "Twice", "Continuously"});
    }

}
