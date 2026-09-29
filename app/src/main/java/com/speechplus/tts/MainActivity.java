package com.speechplus.tts;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextToSpeech tts;
    private Spinner spinnerLanguages;
    private Spinner spinnerEngines;
    private SeekBar seekRate;
    private SeekBar seekPitch;
    private TextView lblRate;
    private TextView lblPitch;
    private Button btnTest;

    private List<TextToSpeech.EngineInfo> enginesList = new ArrayList<>();
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("speech_plus_prefs", MODE_PRIVATE);

        spinnerLanguages = findViewById(R.id.spinnerLanguages);
        spinnerEngines = findViewById(R.id.spinnerEngines);
        seekRate = findViewById(R.id.seekRate);
        seekPitch = findViewById(R.id.seekPitch);
        lblRate = findViewById(R.id.lblRate);
        lblPitch = findViewById(R.id.lblPitch);
        btnTest = findViewById(R.id.btnTestVoice);

        String[] langs = {"English (United States)", "Hindi (India)", "Tamil", "Bengali", "Marathi"};
        ArrayAdapter<String> langAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, langs);
        spinnerLanguages.setAdapter(langAdapter);

        // पिछली सेटिंग्स लोड करें
        float savedRate = prefs.getFloat("rate", 1.0f);
        float savedPitch = prefs.getFloat("pitch", 1.0f);
        seekRate.setProgress((int) (savedRate * 10));
        seekPitch.setProgress((int) (savedPitch * 10));
        lblRate.setText("Speech Rate: " + savedRate + "x");
        lblPitch.setText("Pitch: " + savedPitch + "x");

        seekRate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float r = Math.max(0.5f, progress / 10.0f);
                lblRate.setText("Speech Rate: " + r + "x");
                prefs.edit().putFloat("rate", r).apply();
                if (tts != null) tts.setSpeechRate(r);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekPitch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float p = Math.max(0.5f, progress / 10.0f);
                lblPitch.setText("Pitch: " + p + "x");
                prefs.edit().putFloat("pitch", p).apply();
                if (tts != null) tts.setPitch(p);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        initTTS();

        btnTest.setOnClickListener(v -> {
            if (tts != null) {
                float r = prefs.getFloat("rate", 1.0f);
                float p = prefs.getFloat("pitch", 1.0f);
                tts.setSpeechRate(r);
                tts.setPitch(p);
                tts.speak("This is a sample test of Speech Plus Text to Speech Engine.", TextToSpeech.QUEUE_FLUSH, null, "test_id");
            } else {
                Toast.makeText(MainActivity.this, "TTS Engine Initializing...", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
                enginesList = tts.getEngines();
                List<String> engineNames = new ArrayList<>();
                int selectedIndex = 0;
                String savedEngine = prefs.getString("selected_engine", "");

                for (int i = 0; i < enginesList.size(); i++) {
                    engineNames.add(enginesList.get(i).label);
                    if (enginesList.get(i).name.equals(savedEngine)) {
                        selectedIndex = i;
                    }
                }

                ArrayAdapter<String> engineAdapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_spinner_dropdown_item, engineNames);
                spinnerEngines.setAdapter(engineAdapter);
                if (!engineNames.isEmpty()) {
                    spinnerEngines.setSelection(selectedIndex);
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
