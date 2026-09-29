package com.speechplus.tts;

import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Spinner spinnerLanguages = findViewById(R.id.spinnerLanguages);
        Spinner spinnerEngines = findViewById(R.id.spinnerEngines);
        Button btnTest = findViewById(R.id.btnTestVoice);

        String[] langs = {"Hindi (India)", "English (United States)", "Tamil", "Bengali", "Marathi"};
        ArrayAdapter<String> langAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, langs);
        spinnerLanguages.setAdapter(langAdapter);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                List<String> engineNames = new ArrayList<>();
                for (TextToSpeech.EngineInfo info : tts.getEngines()) {
                    engineNames.add(info.label);
                }
                ArrayAdapter<String> engineAdapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_spinner_dropdown_item, engineNames);
                spinnerEngines.setAdapter(engineAdapter);
            }
        });

        btnTest.setOnClickListener(v -> Toast.makeText(MainActivity.this, "Testing voice configuration...", Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.shutdown();
        }
        super.onDestroy();
    }
}
