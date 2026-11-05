package com.speechplus.tts;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private TextToSpeech testTts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        prefs = getSharedPreferences("SpeechPlusPrefs", Context.MODE_PRIVATE);

        // Start background service
        startService(new Intent(this, SmartAnnouncerService.class));

        initTtsEngine();
        setupAllSpinners();
        setupAllButtons();
    }

    // Pro-Developer Helper to safely populate Spinners
    private void setupSpinner(String idName, String[] items) {
        int resId = getResources().getIdentifier(idName, "id", getPackageName());
        if (resId != 0) {
            Spinner spinner = findViewById(resId);
            if (spinner != null) {
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinner.setAdapter(adapter);
            }
        }
    }

    // Pro-Developer Helper to safely set Button Clicks
    private void setupButtonClick(String idName, View.OnClickListener listener) {
        int resId = getResources().getIdentifier(idName, "id", getPackageName());
        if (resId != 0) {
            View view = findViewById(resId);
            if (view != null) {
                view.setOnClickListener(listener);
            }
        }
    }

    private void setupAllSpinners() {
        // Populating all dead dropdowns to make them alive
        setupSpinner("spinnerCallFormat", new String[]{"Caller Name Only", "Number Only", "Name and Number"});
        setupSpinner("spinnerCallRepeat", new String[]{"Once", "Twice", "Continuously"});
        setupSpinner("spinnerNumFormat", new String[]{"Standard", "Spaced", "Digits"});
        setupSpinner("spinnerNumLang", new String[]{"English", "Hindi", "System Default"});
        setupSpinner("spinnerPunctuation", new String[]{"None", "Some", "All"});
        setupSpinner("spinnerVoiceLanguage", new String[]{"English (US)", "English (India)", "Hindi (India)"});
        setupSpinner("spinnerVoiceEngine", new String[]{"Speech Plus", "Google TTS", "System Default"});
        setupSpinner("spinnerVoiceVariant", new String[]{"Voice 1", "Voice 2", "Voice 3"});
    }

    private void setupAllButtons() {
        // More Tab Action Buttons
        setupButtonClick("btnOpenSystemTts", v -> {
            try {
                Intent intent = new Intent();
                intent.setAction("com.android.settings.TTS_SETTINGS");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Cannot open TTS Settings on this device", Toast.LENGTH_SHORT).show();
            }
        });

        setupButtonClick("btnContactUs", v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:contact.i.t.f.b@gmail.com"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "Speech Plus TTS Feedback");
            try {
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "No email app found!", Toast.LENGTH_SHORT).show();
            }
        });

        setupButtonClick("btnJoinCommunity", v -> {
            Toast.makeText(this, "Opening ITFB Community...", Toast.LENGTH_SHORT).show();
            // We will add the actual community link here later
        });

        setupButtonClick("btnTestVoiceSingle", v -> {
            speakText("This is a test of the Speech Plus TTS engine.");
        });
        
        setupButtonClick("btnOpenSmartReader", v -> startActivity(new Intent(MainActivity.this, SmartReaderActivity.class)));
        setupButtonClick("btnSettingsTimerBattery", v -> startActivity(new Intent(MainActivity.this, SettingsTimerBatteryActivity.class)));
        setupButtonClick("btnSettingsDisplay", v -> startActivity(new Intent(MainActivity.this, SettingsDisplayActivity.class)));
        setupButtonClick("btnSettingsSmartReader", v -> startActivity(new Intent(MainActivity.this, SettingsSmartReaderActivity.class)));
        setupButtonClick("btnSettingsSound", v -> startActivity(new Intent(MainActivity.this, SettingsSoundActivity.class)));

        setupButtonClick("btnPreviewCall", v -> {
            speakText("Incoming call from Paras Yadav");
        });
    }

    private void initTtsEngine() {
        testTts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                // FIXED: Setting TTS to use Accessibility Volume Stream explicitly
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                testTts.setAudioAttributes(audioAttributes);
            }
        });
    }

    private void speakText(String text) {
        if (testTts != null) {
            testTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TEST_UTTERANCE");
            Toast.makeText(this, "Playing Audio...", Toast.LENGTH_SHORT).show();
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
