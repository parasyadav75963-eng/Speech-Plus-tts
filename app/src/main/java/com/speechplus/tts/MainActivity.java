package com.speechplus.tts;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private TextToSpeech tts;
    private Spinner spinnerLanguages, spinnerEngines, spinnerVoices, spinnerAudioRouting;
    private SeekBar seekRate, seekPitch, seekVolume;
    private TextView lblRate, lblPitch, lblVolume;
    private List<TextToSpeech.EngineInfo> enginesList = new ArrayList<>();
    private List<Voice> currentVoices = new ArrayList<>();
    private SharedPreferences prefs;

    private final String CURRENT_VERSION = "1.0";
    private final String REPO_RELEASES_URL = "https://api.github.com/repos/parasyadav75963-eng/Speech-Plus-tts/releases/latest";

    private final String[] languageNames = {
        "Afrikaans", "Arabic", "Assamese", "Bengali", "Bulgarian", "Catalan",
        "Chinese", "Czech", "Danish", "Dutch", "English (UK)", "English (US)",
        "Finnish", "French", "German", "Greek", "Gujarati", "Hebrew", "Hindi",
        "Hungarian", "Indonesian", "Italian", "Japanese", "Kannada", "Korean",
        "Malayalam", "Marathi", "Nepali", "Norwegian", "Odia", "Polish",
        "Portuguese", "Punjabi", "Romanian", "Russian", "Sanskrit", "Spanish",
        "Swedish", "Tamil", "Telugu", "Thai", "Turkish", "Ukrainian", "Urdu", "Vietnamese"
    };

    private final String[] languageCodes = {
        "af", "ar", "as", "bn", "bg", "ca",
        "zh", "cs", "da", "nl", "en-GB", "en-US",
        "fi", "fr", "de", "el", "gu", "he", "hi",
        "hu", "id", "it", "ja", "kn", "ko",
        "ml", "mr", "ne", "no", "or", "pl",
        "pt", "pa", "ro", "ru", "sa", "es",
        "sv", "ta", "te", "th", "tr", "uk", "ur", "vi"
    };

    private final String[] routingOptions = {
        "Accessibility (TalkBack Stream)", "Media Stream", "Notification Stream", "Alarm Stream", "Ring Stream"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("speech_plus_prefs", MODE_PRIVATE);

        spinnerLanguages = findViewById(R.id.spinnerLanguages);
        spinnerEngines = findViewById(R.id.spinnerEngines);
        spinnerVoices = findViewById(R.id.spinnerVoices);
        spinnerAudioRouting = findViewById(R.id.spinnerAudioRouting);
        seekRate = findViewById(R.id.seekRate);
        seekPitch = findViewById(R.id.seekPitch);
        seekVolume = findViewById(R.id.seekVolume);
        lblRate = findViewById(R.id.lblRate);
        lblPitch = findViewById(R.id.lblPitch);
        lblVolume = findViewById(R.id.lblVolume);

        spinnerLanguages.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, languageNames));
        spinnerAudioRouting.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, routingOptions));

        loadSettings();
        setupEvents();
        loadEngines();
        checkForUpdates(false);
    }
    private void loadSettings() {
        float r = prefs.getFloat("rate", 1.0f);
        float p = prefs.getFloat("pitch", 1.0f);
        int v = prefs.getInt("volume", 100);
        seekRate.setProgress((int)(r * 10));
        seekPitch.setProgress((int)(p * 10));
        seekVolume.setProgress(v);
        lblRate.setText("Speech Rate: " + r + "x");
        lblPitch.setText("Pitch: " + p + "x");
        lblVolume.setText("Volume: " + v + "%");
        spinnerLanguages.setSelection(prefs.getInt("lang_pos", 11));
        spinnerAudioRouting.setSelection(prefs.getInt("route_pos", 0));
    }

    private void setupEvents() {
        seekRate.setOnSeekBarChangeListener(new SimpleListener(prg -> lblRate.setText("Speech Rate: " + Math.max(0.2f, prg / 10.0f) + "x")));
        seekPitch.setOnSeekBarChangeListener(new SimpleListener(prg -> lblPitch.setText("Pitch: " + Math.max(0.2f, prg / 10.0f) + "x")));
        seekVolume.setOnSeekBarChangeListener(new SimpleListener(prg -> lblVolume.setText("Volume: " + prg + "%")));

        findViewById(R.id.btnTestVoice).setOnClickListener(v -> speakTest());
        findViewById(R.id.btnStopSpeaking).setOnClickListener(v -> { if (tts != null) tts.stop(); });

        findViewById(R.id.btnSaveSettings).setOnClickListener(v -> {
            SharedPreferences.Editor ed = prefs.edit();
            ed.putFloat("rate", Math.max(0.2f, seekRate.getProgress() / 10.0f));
            ed.putFloat("pitch", Math.max(0.2f, seekPitch.getProgress() / 10.0f));
            ed.putInt("volume", seekVolume.getProgress());
            ed.putInt("lang_pos", spinnerLanguages.getSelectedItemPosition());
            ed.putString("lang_code", languageCodes[spinnerLanguages.getSelectedItemPosition()]);
            ed.putInt("route_pos", spinnerAudioRouting.getSelectedItemPosition());
            if (!enginesList.isEmpty() && spinnerEngines.getSelectedItem() != null) {
                ed.putString("selected_engine", enginesList.get(spinnerEngines.getSelectedItemPosition()).name);
            }
            if (!currentVoices.isEmpty() && spinnerVoices.getSelectedItem() != null) {
                ed.putString("selected_voice", currentVoices.get(spinnerVoices.getSelectedItemPosition()).getName());
            }
            ed.apply();
            initEngine(prefs.getString("selected_engine", null));
            Toast.makeText(this, "Settings Saved!", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnCheckUpdate).setOnClickListener(v -> checkForUpdates(true));
        findViewById(R.id.btnAbout).setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));

        spinnerEngines.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (!enginesList.isEmpty()) initEngine(enginesList.get(pos).name);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        spinnerLanguages.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { populateVoices(); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private void loadEngines() {
        TextToSpeech helper = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                enginesList = helper.getEngines();
                List<String> labels = new ArrayList<>();
                int sel = 0;
                String saved = prefs.getString("selected_engine", "");
                for (int i = 0; i < enginesList.size(); i++) {
                    labels.add(enginesList.get(i).label);
                    if (enginesList.get(i).name.equals(saved)) sel = i;
                }
                spinnerEngines.setAdapter(new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_spinner_dropdown_item, labels));
                if (!labels.isEmpty()) spinnerEngines.setSelection(sel);
                helper.shutdown();
                initEngine(saved.isEmpty() ? null : saved);
            }
        });
    }

    private void initEngine(String enginePkg) {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        tts = new TextToSpeech(getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                populateVoices();
                applyParams();
            }
        }, enginePkg);
    }

    private void populateVoices() {
        if (tts == null) return;
        try {
            int langPos = spinnerLanguages.getSelectedItemPosition();
            Locale targetLocale = Locale.forLanguageTag(languageCodes[langPos]);
            currentVoices.clear();
            List<String> voiceLabels = new ArrayList<>();
            String savedVoice = prefs.getString("selected_voice", "");
            int sel = 0;
            if (tts.getVoices() != null) {
                for (Voice v : tts.getVoices()) {
                    if (v.getLocale().getLanguage().equals(targetLocale.getLanguage())) {
                        currentVoices.add(v);
                        voiceLabels.add(v.getName());
                        if (v.getName().equals(savedVoice)) sel = voiceLabels.size() - 1;
                    }
                }
            }
            if (voiceLabels.isEmpty()) voiceLabels.add("Default Voice");
            spinnerVoices.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, voiceLabels));
            spinnerVoices.setSelection(sel);
        } catch (Exception ignored) {}
    }

    private void applyParams() {
        if (tts == null) return;
        tts.setLanguage(Locale.forLanguageTag(languageCodes[spinnerLanguages.getSelectedItemPosition()]));
        tts.setSpeechRate(prefs.getFloat("rate", 1.0f));
        tts.setPitch(prefs.getFloat("pitch", 1.0f));
        if (!currentVoices.isEmpty() && spinnerVoices.getSelectedItemPosition() < currentVoices.size()) {
            try { tts.setVoice(currentVoices.get(spinnerVoices.getSelectedItemPosition())); } catch (Exception ignored) {}
        }
        int route = prefs.getInt("route_pos", 0);
        AudioAttributes.Builder ab = new AudioAttributes.Builder();
        if (route == 0) ab.setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH);
        else if (route == 2) ab.setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION);
        else if (route == 3) ab.setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION);
        else ab.setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH);
        tts.setAudioAttributes(ab.build());
    }

    private void speakTest() {
        if (tts != null) {
            applyParams();
            String txt = "This is a test of Speech Plus Text to Speech engine.";
            if (languageCodes[spinnerLanguages.getSelectedItemPosition()].startsWith("hi")) {
                txt = "यह स्पीच प्लस का परीक्षण है।";
            }
            Bundle b = new Bundle();
            b.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, prefs.getInt("volume", 100) / 100.0f);
            tts.speak(txt, TextToSpeech.QUEUE_FLUSH, b, "test_utterance");
        }
    }

    private void checkForUpdates(boolean manual) {
        new Thread(() -> {
            try {
                URL url = new URL(REPO_RELEASES_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "SpeechPlus-App");
                conn.setConnectTimeout(4000);
                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    JSONObject json = new JSONObject(sb.toString());
                    String latestTag = json.optString("tag_name", "").replace("v", "");
                    String downloadUrl = json.optString("html_url", "");
                    if (!latestTag.isEmpty() && !latestTag.equals(CURRENT_VERSION)) {
                        runOnUiThread(() -> showUpdateDialog(latestTag, downloadUrl));
                    } else if (manual) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "App is up to date! (v" + CURRENT_VERSION + ")", Toast.LENGTH_SHORT).show());
                    }
                } else if (manual) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "No new update found.", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                if (manual) runOnUiThread(() -> Toast.makeText(MainActivity.this, "Could not check update.", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void showUpdateDialog(String newVersion, String url) {
        new AlertDialog.Builder(this)
                .setTitle("Update Available!")
                .setMessage("Version " + newVersion + " is available. Update now?")
                .setPositiveButton("UPDATE NOW", (dialog, which) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))))
                .setNegativeButton("LATER", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }

    interface Action { void run(int p); }
    static class SimpleListener implements SeekBar.OnSeekBarChangeListener {
        private final Action action;
        SimpleListener(Action action) { this.action = action; }
        @Override public void onProgressChanged(SeekBar s, int p, boolean b) { action.run(p); }
        @Override public void onStartTrackingTouch(SeekBar s) {}
        @Override public void onStopTrackingTouch(SeekBar s) {}
    }
}
}
