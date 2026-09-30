package com.speechplus.tts;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    public static final String CURRENT_VERSION = "1.2.0";
    private static final String REPO_RELEASES_URL = "https://api.github.com/repos/parasyadav75963-eng/Speech-Plus-tts/releases/latest";

    private SharedPreferences prefs;
    private TextToSpeech ttsHelper;

    private Spinner spinnerModes, spinnerLanguages, spinnerEngines, spinnerVoices, spinnerAudioRouting;
    private SeekBar seekRate, seekPitch, seekVolume;
    private TextView lblRate, lblPitch, lblVolume;

    private final String[] modes = {"Single Language Mode", "Dual Language Mode", "Mix Mode (Auto Detect)"};
    private final String[] languageNames = {
        "Default / System", "Hindi (हिन्दी)", "English (India)", "English (US)", "English (UK)", 
        "Bengali (বাংলা)", "Gujarati (ગુજરાતી)", "Kannada (ಕನ್ನಡ)", "Malayalam (മലയാളം)", 
        "Marathi (मराठी)", "Punjabi (ਪੰਜਾਬੀ)", "Tamil (தமிழ்)", "Telugu (తెలుగు)", "Urdu (اردو)",
        "Spanish", "French", "German", "Russian", "Arabic", "Portuguese", "Japanese"
    };
    private final String[] languageCodes = {
        "", "hi_IN", "en_IN", "en_US", "en_GB", 
        "bn_IN", "gu_IN", "kn_IN", "ml_IN", 
        "mr_IN", "pa_IN", "ta_IN", "te_IN", "ur_IN",
        "es_ES", "fr_FR", "de_DE", "ru_RU", "ar", "pt_BR", "ja_JP"
    };

    private final String[] routingOptions = {"Accessibility Assistance (TalkBack)", "Media Audio Stream", "Notification Stream"};

    private List<TextToSpeech.EngineInfo> installedEngines = new ArrayList<>();
    private List<Voice> availableVoices = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("speech_plus_prefs", MODE_PRIVATE);

        spinnerModes = findViewById(R.id.spinnerModes);
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

        setupSpinners();
        setupSeekBars();

        ttsHelper = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                runOnUiThread(this::loadEnginesAndVoices);
            }
        });

        Button btnTestVoice = findViewById(R.id.btnTestVoice);
        if (btnTestVoice != null) {
            btnTestVoice.setOnClickListener(v -> testSpeech());
        }

        Button btnStopSpeaking = findViewById(R.id.btnStopSpeaking);
        if (btnStopSpeaking != null) {
            btnStopSpeaking.setOnClickListener(v -> {
                if (ttsHelper != null) ttsHelper.stop();
            });
        }

        Button btnSaveSettings = findViewById(R.id.btnSaveSettings);
        if (btnSaveSettings != null) {
            btnSaveSettings.setOnClickListener(v -> Toast.makeText(this, "Settings saved successfully!", Toast.LENGTH_SHORT).show());
        }

        Button btnCheckUpdate = findViewById(R.id.btnCheckUpdate);
        if (btnCheckUpdate != null) {
            btnCheckUpdate.setOnClickListener(v -> checkForUpdates(true));
        }

        Button btnAbout = findViewById(R.id.btnAbout);
        if (btnAbout != null) {
            btnAbout.setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));
        }
    }

    private void setupSpinners() {
        if (spinnerModes != null) {
            ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modes);
            spinnerModes.setAdapter(modeAdapter);
            spinnerModes.setSelection(prefs.getInt("tts_mode", 0));
            spinnerModes.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    prefs.edit().putInt("tts_mode", pos).apply();
                }
                @Override public void onNothingSelected(AdapterView<?> p) {}
            });
        }

        if (spinnerLanguages != null) {
            ArrayAdapter<String> langAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, languageNames);
            spinnerLanguages.setAdapter(langAdapter);
            spinnerLanguages.setSelection(prefs.getInt("selected_lang_pos", 0));
            spinnerLanguages.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    prefs.edit().putInt("selected_lang_pos", pos)
                                .putString("selected_lang_code", languageCodes[pos]).apply();
                }
                @Override public void onNothingSelected(AdapterView<?> p) {}
            });
        }

        if (spinnerAudioRouting != null) {
            ArrayAdapter<String> routeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, routingOptions);
            spinnerAudioRouting.setAdapter(routeAdapter);
            spinnerAudioRouting.setSelection(prefs.getInt("audio_routing", 0));
            spinnerAudioRouting.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    prefs.edit().putInt("audio_routing", pos).apply();
                }
                @Override public void onNothingSelected(AdapterView<?> p) {}
            });
        }
    }

    private void loadEnginesAndVoices() {
        if (ttsHelper == null) return;

        installedEngines = ttsHelper.getEngines();
        List<String> engineNames = new ArrayList<>();
        int selectedIdx = 0;
        String savedEngine = prefs.getString("selected_engine", "");

        for (int i = 0; i < installedEngines.size(); i++) {
            engineNames.add(installedEngines.get(i).label);
            if (installedEngines.get(i).name.equals(savedEngine)) {
                selectedIdx = i;
            }
        }

        if (spinnerEngines != null && !engineNames.isEmpty()) {
            ArrayAdapter<String> engineAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, engineNames);
            spinnerEngines.setAdapter(engineAdapter);
            spinnerEngines.setSelection(selectedIdx);
            spinnerEngines.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    prefs.edit().putString("selected_engine", installedEngines.get(pos).name).apply();
                }
                @Override public void onNothingSelected(AdapterView<?> p) {}
            });
        }

        try {
            availableVoices = new ArrayList<>(ttsHelper.getVoices());
            List<String> voiceNames = new ArrayList<>();
            for (Voice vc : availableVoices) {
                voiceNames.add(vc.getName());
            }
            if (spinnerVoices != null && !voiceNames.isEmpty()) {
                ArrayAdapter<String> voiceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, voiceNames);
                spinnerVoices.setAdapter(voiceAdapter);
            }
        } catch (Exception ignored) {}
    }

    private void setupSeekBars() {
        if (seekRate != null) {
            seekRate.setProgress((int) (prefs.getFloat("rate", 1.0f) * 50));
            seekRate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar s, int p, boolean f) {
                    float r = Math.max(0.1f, p / 50.0f);
                    prefs.edit().putFloat("rate", r).apply();
                    if (lblRate != null) lblRate.setText(String.format(Locale.US, "Speech Rate: %.2fx", r));
                }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });
        }

        if (seekPitch != null) {
            seekPitch.setProgress((int) (prefs.getFloat("pitch", 1.0f) * 50));
            seekPitch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar s, int p, boolean f) {
                    float pi = Math.max(0.1f, p / 50.0f);
                    prefs.edit().putFloat("pitch", pi).apply();
                    if (lblPitch != null) lblPitch.setText(String.format(Locale.US, "Pitch: %.2fx", pi));
                }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });
        }

        if (seekVolume != null) {
            seekVolume.setProgress(prefs.getInt("volume", 100));
            seekVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar s, int p, boolean f) {
                    prefs.edit().putInt("volume", p).apply();
                    if (lblVolume != null) lblVolume.setText("Volume: " + p + "%");
                }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });
        }
    }

    private void testSpeech() {
        if (ttsHelper != null) {
            ttsHelper.setSpeechRate(prefs.getFloat("rate", 1.0f));
            ttsHelper.setPitch(prefs.getFloat("pitch", 1.0f));
            ttsHelper.speak("भाषण प्लस टीटीएस में आपका स्वागत है। Welcome to Speech Plus TTS engine.", TextToSpeech.QUEUE_FLUSH, null, "test_utterance");
        }
    }

    private void openCommunityLink() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/blind_tech_world"));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open community link", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Settings & Accessibility");
        menu.add(0, 2, 1, "Help & Feedback");
        menu.add(0, 3, 2, "Join Community");
        menu.add(0, 4, 3, "About Speech Plus");
        menu.add(0, 5, 4, "Check for Updates");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == 1) {
            showSettingsDialog();
            return true;
        } else if (id == 2) {
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:contact.itfb@gmail.com"));
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Speech Plus Support & Feedback");
            startActivity(Intent.createChooser(emailIntent, "Send Feedback"));
            return true;
        } else if (id == 3) {
            openCommunityLink();
            return true;
        } else if (id == 4) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        } else if (id == 5) {
            checkForUpdates(true);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Settings & Accessibility");

        String[] options = {"Force Speech Rate (Ignore TalkBack speed)", "Force Pitch (Ignore TalkBack pitch)"};
        boolean[] checked = {
            prefs.getBoolean("force_rate", false),
            prefs.getBoolean("force_pitch", false)
        };

        builder.setMultiChoiceItems(options, checked, (dialog, which, isChecked) -> {
            if (which == 0) {
                prefs.edit().putBoolean("force_rate", isChecked).apply();
            } else if (which == 1) {
                prefs.edit().putBoolean("force_pitch", isChecked).apply();
            }
        });

        builder.setPositiveButton("Done", null);
        builder.show();
    }

    public void checkForUpdates(boolean manual) {
        if (manual) Toast.makeText(this, "Checking for latest updates...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                URL url = new URL(REPO_RELEASES_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "SpeechPlus-App");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                int responseCode = conn.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    JSONObject json = new JSONObject(sb.toString());
                    String latestTag = json.optString("tag_name", "").replace("v", "").trim();

                    String downloadUrl = "";
                    JSONArray assets = json.optJSONArray("assets");
                    if (assets != null && assets.length() > 0) {
                        downloadUrl = assets.getJSONObject(0).optString("browser_download_url", "");
                    }
                    if (downloadUrl.isEmpty()) {
                        downloadUrl = json.optString("html_url", "");
                    }

                    final String finalUrl = downloadUrl;
                    if (!latestTag.isEmpty() && !latestTag.equals(CURRENT_VERSION)) {
                        runOnUiThread(() -> showUpdateDialog(latestTag, finalUrl));
                    } else if (manual) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "App is up to date! (v" + CURRENT_VERSION + ")", Toast.LENGTH_LONG).show());
                    }
                } else if (manual) {
                    final int code = responseCode;
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "No updates found (" + code + ")", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                if (manual) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to check update: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            }
        }).start();
    }

    private void showUpdateDialog(String newVersion, String downloadUrl) {
        new AlertDialog.Builder(this)
                .setTitle("New Update Available!")
                .setMessage("Speech Plus TTS version " + newVersion + " is now available.\n\nClick Update to download and install.")
                .setPositiveButton("Update Now", (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl));
                    startActivity(intent);
                })
                .setNegativeButton("Later", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        if (ttsHelper != null) {
            ttsHelper.stop();
            ttsHelper.shutdown();
        }
        super.onDestroy();
    }
}
