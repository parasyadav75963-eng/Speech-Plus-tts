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
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    public static final String CURRENT_VERSION = "1.2.0";
    private static final String REPO_RELEASES_URL = "https://api.github.com/repos/parasyadav75963-eng/Speech-Plus-tts/releases/latest";

    private SharedPreferences prefs;
    private TextToSpeech ttsInitHelper;

    private Spinner spinnerModes, spinnerLanguages, spinnerEngines, spinnerSecondaryEngines, spinnerVoices;
    private SeekBar seekRate, seekPitch;
    private TextView lblRate, lblPitch;

    private final String[] modes = {"Single Language Mode", "Dual Language Mode", "Mix Mode (Auto Detect)"};
    private final String[] languages = {
        "Default / System", "Hindi (India)", "English (India)", "English (US)", "English (UK)", 
        "Bengali (India)", "Gujarati (India)", "Kannada (India)", "Malayalam (India)", 
        "Marathi (India)", "Punjabi (India)", "Tamil (India)", "Telugu (India)", "Urdu (India)"
    };
    private final String[] langCodes = {
        "", "hi_IN", "en_IN", "en_US", "en_GB", 
        "bn_IN", "gu_IN", "kn_IN", "ml_IN", 
        "mr_IN", "pa_IN", "ta_IN", "te_IN", "ur_IN"
    };

    private List<TextToSpeech.EngineInfo> installedEngines = new ArrayList<>();
    private List<Voice> availableVoices = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("speech_plus_prefs", MODE_PRIVATE);

        // Bind Views Safely
        spinnerModes = findSafeView("spinnerModes", Spinner.class);
        spinnerLanguages = findSafeView("spinnerLanguages", Spinner.class);
        spinnerEngines = findSafeView("spinnerEngines", Spinner.class);
        spinnerSecondaryEngines = findSafeView("spinnerSecondaryEngines", Spinner.class);
        spinnerVoices = findSafeView("spinnerVoices", Spinner.class);
        seekRate = findSafeView("seekRate", SeekBar.class);
        seekPitch = findSafeView("seekPitch", SeekBar.class);
        lblRate = findSafeView("lblRate", TextView.class);
        lblPitch = findSafeView("lblPitch", TextView.class);

        setupModeSpinner();
        setupLanguageSpinner();
        setupSeekBars();

        ttsInitHelper = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                runOnUiThread(this::loadEnginesAndVoices);
            }
        });

        View btnTest = findSafeView("btnTest", View.class);
        if (btnTest != null) {
            btnTest.setOnClickListener(v -> testSpeech());
        }

        View btnCheckUpdate = findSafeView("btnCheckUpdate", View.class);
        if (btnCheckUpdate != null) {
            btnCheckUpdate.setOnClickListener(v -> checkForUpdates(true));
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends View> T findSafeView(String name, Class<T> type) {
        int id = getResources().getIdentifier(name, "id", getPackageName());
        return id != 0 ? (T) findViewById(id) : null;
    }

    private void setupModeSpinner() {
        if (spinnerModes != null) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modes);
            spinnerModes.setAdapter(adapter);
            spinnerModes.setSelection(prefs.getInt("tts_mode", 0));
            spinnerModes.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    prefs.edit().putInt("tts_mode", pos).apply();
                }
                @Override
                public void onNothingSelected(AdapterView<?> p) {}
            });
        }
    }

    private void setupLanguageSpinner() {
        if (spinnerLanguages != null) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, languages);
            spinnerLanguages.setAdapter(adapter);
            spinnerLanguages.setSelection(prefs.getInt("selected_lang_pos", 0));
            spinnerLanguages.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    prefs.edit().putInt("selected_lang_pos", pos)
                                .putString("selected_lang_code", langCodes[pos]).apply();
                }
                @Override
                public void onNothingSelected(AdapterView<?> p) {}
            });
        }
    }

    private void loadEnginesAndVoices() {
        if (ttsInitHelper == null) return;

        installedEngines = ttsInitHelper.getEngines();
        List<String> engineNames = new ArrayList<>();
        int selectedEngineIdx = 0;
        String savedEngine = prefs.getString("selected_engine", "");

        for (int i = 0; i < installedEngines.size(); i++) {
            engineNames.add(installedEngines.get(i).label);
            if (installedEngines.get(i).name.equals(savedEngine)) {
                selectedEngineIdx = i;
            }
        }

        if (spinnerEngines != null && !engineNames.isEmpty()) {
            ArrayAdapter<String> engineAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, engineNames);
            spinnerEngines.setAdapter(engineAdapter);
            spinnerEngines.setSelection(selectedEngineIdx);
            spinnerEngines.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    prefs.edit().putString("selected_engine", installedEngines.get(pos).name).apply();
                }
                @Override
                public void onNothingSelected(AdapterView<?> p) {}
            });
        }

        if (spinnerSecondaryEngines != null && !engineNames.isEmpty()) {
            ArrayAdapter<String> secAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, engineNames);
            spinnerSecondaryEngines.setAdapter(secAdapter);
            spinnerSecondaryEngines.setSelection(selectedEngineIdx);
            spinnerSecondaryEngines.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    prefs.edit().putString("secondary_engine", installedEngines.get(pos).name).apply();
                }
                @Override
                public void onNothingSelected(AdapterView<?> p) {}
            });
        }

        try {
            availableVoices = new ArrayList<>(ttsInitHelper.getVoices());
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
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    float r = Math.max(0.1f, progress / 50.0f);
                    prefs.edit().putFloat("rate", r).apply();
                    if (lblRate != null) lblRate.setText(String.format(Locale.US, "Speech Rate: %.2fx", r));
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        if (seekPitch != null) {
            seekPitch.setProgress((int) (prefs.getFloat("pitch", 1.0f) * 50));
            seekPitch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    float p = Math.max(0.1f, progress / 50.0f);
                    prefs.edit().putFloat("pitch", p).apply();
                    if (lblPitch != null) lblPitch.setText(String.format(Locale.US, "Pitch: %.2fx", p));
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
    }

    private void testSpeech() {
        if (ttsInitHelper != null) {
            ttsInitHelper.setSpeechRate(prefs.getFloat("rate", 1.0f));
            ttsInitHelper.setPitch(prefs.getFloat("pitch", 1.0f));
            ttsInitHelper.speak("This is a test of Speech Plus TTS engine. भाषण प्लस टीटीएस में आपका स्वागत है।", TextToSpeech.QUEUE_FLUSH, null, "test");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Settings & Accessibility");
        menu.add(0, 2, 1, "Help & Feedback");
        menu.add(0, 3, 2, "About Speech Plus");
        menu.add(0, 4, 3, "Check for Updates");
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
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        } else if (id == 4) {
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
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "No updates found or server error (" + code + ")", Toast.LENGTH_SHORT).show());
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
        if (ttsInitHelper != null) {
            ttsInitHelper.stop();
            ttsInitHelper.shutdown();
        }
        super.onDestroy();
    }
}
