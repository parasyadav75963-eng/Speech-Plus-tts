package com.speechplus.tts;

import android.app.ProgressDialog;
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
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    public static final String CURRENT_VERSION = "1.2.1";
    private static final String REPO_RELEASES_URL = "https://api.github.com/repos/parasyadav75963-eng/Speech-Plus-tts/releases/latest";

    private SharedPreferences prefs;
    private TextToSpeech ttsHelper;

    private Spinner spinnerModes, spinnerLanguages, spinnerSecondaryLanguages, spinnerEngines, spinnerSecondaryEngines, spinnerVoices, spinnerAudioRouting;
    private LinearLayout layoutSecondary;
    private SeekBar seekRate, seekPitch, seekVolume;
    private TextView lblRate, lblPitch, lblVolume;
    private CheckBox chkForceRate, chkForcePitch;

    private final String[] modes = {"Single Language Mode", "Dual Language Mode", "Mix Mode (Auto Detect)"};
    private final String[] languageNames = {
        "Default / System", "Hindi (हिन्दी)", "English (India)", "English (US)", "English (UK)", 
        "Bengali (বাংলা)", "Gujarati (ગુજરાતી)", "Kannada (ಕನ್ನಡ)", "Malayalam (മലയാളം)", 
        "Marathi (मराठी)", "Punjabi (ਪੰਜਾਬੀ)", "Tamil (தமிழ்)", "Telugu (తెలుగు)", "Urdu (اردو)",
        "Spanish", "French", "German", "Russian", "Arabic"
    };
    private final String[] languageCodes = {
        "", "hi_IN", "en_IN", "en_US", "en_GB", 
        "bn_IN", "gu_IN", "kn_IN", "ml_IN", 
        "mr_IN", "pa_IN", "ta_IN", "te_IN", "ur_IN",
        "es_ES", "fr_FR", "de_DE", "ru_RU", "ar"
    };

    private final String[] routingOptions = {"Accessibility Assistance (TalkBack)", "Media Audio Stream", "Notification Stream"};
    private List<TextToSpeech.EngineInfo> installedEngines = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("speech_plus_prefs", MODE_PRIVATE);

        spinnerModes = findViewById(R.id.spinnerModes);
        spinnerLanguages = findViewById(R.id.spinnerLanguages);
        spinnerSecondaryLanguages = findViewById(R.id.spinnerSecondaryLanguages);
        spinnerEngines = findViewById(R.id.spinnerEngines);
        spinnerSecondaryEngines = findViewById(R.id.spinnerSecondaryEngines);
        spinnerVoices = findViewById(R.id.spinnerVoices);
        spinnerAudioRouting = findViewById(R.id.spinnerAudioRouting);
        layoutSecondary = findViewById(R.id.layoutSecondary);

        seekRate = findViewById(R.id.seekRate);
        seekPitch = findViewById(R.id.seekPitch);
        seekVolume = findViewById(R.id.seekVolume);

        lblRate = findViewById(R.id.lblRate);
        lblPitch = findViewById(R.id.lblPitch);
        lblVolume = findViewById(R.id.lblVolume);

        chkForceRate = findViewById(R.id.chkForceRate);
        chkForcePitch = findViewById(R.id.chkForcePitch);

        if (chkForceRate != null) {
            chkForceRate.setChecked(prefs.getBoolean("force_rate", false));
            chkForceRate.setOnCheckedChangeListener((btn, isChecked) -> prefs.edit().putBoolean("force_rate", isChecked).apply());
        }

        if (chkForcePitch != null) {
            chkForcePitch.setChecked(prefs.getBoolean("force_pitch", false));
            chkForcePitch.setOnCheckedChangeListener((btn, isChecked) -> prefs.edit().putBoolean("force_pitch", isChecked).apply());
        }

        Button btnMoreOptions = findViewById(R.id.btnMoreOptions);
        if (btnMoreOptions != null) {
            btnMoreOptions.setOnClickListener(v -> showMoreOptionsMenu());
        }

        setupSpinners();
        setupSeekBars();

        ttsHelper = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                runOnUiThread(this::loadEnginesAndVoices);
            }
        });

        Button btnTestVoice = findViewById(R.id.btnTestVoice);
        if (btnTestVoice != null) btnTestVoice.setOnClickListener(v -> testSpeech());

        Button btnStopSpeaking = findViewById(R.id.btnStopSpeaking);
        if (btnStopSpeaking != null) {
            btnStopSpeaking.setOnClickListener(v -> { if (ttsHelper != null) ttsHelper.stop(); });
        }

        Button btnSaveSettings = findViewById(R.id.btnSaveSettings);
        if (btnSaveSettings != null) {
            btnSaveSettings.setOnClickListener(v -> Toast.makeText(this, "Settings saved successfully!", Toast.LENGTH_SHORT).show());
        }

        Button btnCheckUpdate = findViewById(R.id.btnCheckUpdate);
        if (btnCheckUpdate != null) btnCheckUpdate.setOnClickListener(v -> checkForUpdates(true));
    }

    private void showMoreOptionsMenu() {
        String[] menuItems = {
            "Settings & Accessibility",
            "Help & Feedback",
            "Join Community (Telegram)",
            "About Speech Plus",
            "Check for Updates"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("More Options");
        builder.setItems(menuItems, (dialog, which) -> {
            switch (which) {
                case 0:
                    showSettingsDialog();
                    break;
                case 1:
                    Intent emailIntent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:contact.itfb@gmail.com"));
                    emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Speech Plus Support & Feedback");
                    startActivity(Intent.createChooser(emailIntent, "Send Feedback"));
                    break;
                case 2:
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/blind_tech_world")));
                    break;
                case 3:
                    startActivity(new Intent(this, AboutActivity.class));
                    break;
                case 4:
                    checkForUpdates(true);
                    break;
            }
        });
        builder.show();
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Settings & Accessibility");

        String[] options = {
            "Force Speech Rate (Ignore TalkBack speed)",
            "Force Pitch (Ignore TalkBack pitch)"
        };
        boolean[] checked = {
            prefs.getBoolean("force_rate", false),
            prefs.getBoolean("force_pitch", false)
        };

        builder.setMultiChoiceItems(options, checked, (dialog, which, isChecked) -> {
            if (which == 0) {
                prefs.edit().putBoolean("force_rate", isChecked).apply();
                if (chkForceRate != null) chkForceRate.setChecked(isChecked);
            } else if (which == 1) {
                prefs.edit().putBoolean("force_pitch", isChecked).apply();
                if (chkForcePitch != null) chkForcePitch.setChecked(isChecked);
            }
        });

        builder.setPositiveButton("Done", null);
        builder.show();
    }

    private void setupSpinners() {
        if (spinnerModes != null) {
            ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modes);
            spinnerModes.setAdapter(modeAdapter);
            int savedMode = prefs.getInt("tts_mode", 0);
            spinnerModes.setSelection(savedMode);
            updateSecondaryVisibility(savedMode);

            spinnerModes.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    prefs.edit().putInt("tts_mode", pos).apply();
                    updateSecondaryVisibility(pos);
                }
                @Override public void onNothingSelected(AdapterView<?> p) {}
            });
        }

        ArrayAdapter<String> langAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, languageNames);
        if (spinnerLanguages != null) {
            spinnerLanguages.setAdapter(langAdapter);
            spinnerLanguages.setSelection(prefs.getInt("selected_lang_pos", 0));
            spinnerLanguages.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    prefs.edit().putInt("selected_lang_pos", pos).putString("selected_lang_code", languageCodes[pos]).apply();
                }
                @Override public void onNothingSelected(AdapterView<?> p) {}
            });
        }

        if (spinnerSecondaryLanguages != null) {
            spinnerSecondaryLanguages.setAdapter(langAdapter);
            spinnerSecondaryLanguages.setSelection(prefs.getInt("sec_lang_pos", 2));
            spinnerSecondaryLanguages.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    prefs.edit().putInt("sec_lang_pos", pos).putString("sec_lang_code", languageCodes[pos]).apply();
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

    private void updateSecondaryVisibility(int mode) {
        if (layoutSecondary != null) {
            layoutSecondary.setVisibility((mode == 1 || mode == 2) ? View.VISIBLE : View.GONE);
        }
    }

    private void loadEnginesAndVoices() {
        if (ttsHelper == null) return;
        installedEngines = ttsHelper.getEngines();
        List<String> engineNames = new ArrayList<>();
        int pIdx = 0, sIdx = 0;
        String pEng = prefs.getString("selected_engine", "");
        String sEng = prefs.getString("secondary_engine", "");

        for (int i = 0; i < installedEngines.size(); i++) {
            engineNames.add(installedEngines.get(i).label);
            if (installedEngines.get(i).name.equals(pEng)) pIdx = i;
            if (installedEngines.get(i).name.equals(sEng)) sIdx = i;
        }

        ArrayAdapter<String> engAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, engineNames);
        if (spinnerEngines != null && !engineNames.isEmpty()) {
            spinnerEngines.setAdapter(engAdapter);
            spinnerEngines.setSelection(pIdx);
            spinnerEngines.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    prefs.edit().putString("selected_engine", installedEngines.get(pos).name).apply();
                }
                @Override public void onNothingSelected(AdapterView<?> p) {}
            });
        }

        if (spinnerSecondaryEngines != null && !engineNames.isEmpty()) {
            ArrayAdapter<String> secEngAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, engineNames);
            spinnerSecondaryEngines.setAdapter(secEngAdapter);
            spinnerSecondaryEngines.setSelection(sIdx);
            spinnerSecondaryEngines.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    prefs.edit().putString("secondary_engine", installedEngines.get(pos).name).apply();
                }
                @Override public void onNothingSelected(AdapterView<?> p) {}
            });
        }

        try {
            List<Voice> voices = new ArrayList<>(ttsHelper.getVoices());
            List<String> voiceNames = new ArrayList<>();
            for (Voice vc : voices) voiceNames.add(vc.getName());
            if (spinnerVoices != null && !voiceNames.isEmpty()) {
                spinnerVoices.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, voiceNames));
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
            ttsHelper.speak("भाषण प्लस टीटीएस में आपका स्वागत है। Welcome to Speech Plus TTS.", TextToSpeech.QUEUE_FLUSH, null, "test");
        }
    }

    public void checkForUpdates(boolean manual) {
        if (manual) Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show();
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

                    final String apkUrl = downloadUrl;
                    if (!latestTag.isEmpty() && !latestTag.equals(CURRENT_VERSION)) {
                        runOnUiThread(() -> showUpdateDialog(latestTag, apkUrl));
                    } else if (manual) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Speech Plus is up to date! (v" + CURRENT_VERSION + ")", Toast.LENGTH_LONG).show());
                    }
                }
            } catch (Exception e) {
                if (manual) runOnUiThread(() -> Toast.makeText(MainActivity.this, "Check failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void showUpdateDialog(String version, String apkUrl) {
        new AlertDialog.Builder(this)
                .setTitle("Update Available!")
                .setMessage("Speech Plus TTS version " + version + " is ready.\nDownload and install directly?")
                .setPositiveButton("Install Update", (d, w) -> startInAppDownload(apkUrl))
                .setNegativeButton("Later", null)
                .show();
    }

    private void startInAppDownload(String apkUrl) {
        ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle("Downloading Speech Plus Update");
        progress.setMessage("Downloading APK, please wait...");
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setCancelable(false);
        progress.setMax(100);
        progress.show();

        new Thread(() -> {
            try {
                URL url = new URL(apkUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.connect();
                int fileLength = conn.getContentLength();

                File file = new File(getExternalCacheDir(), "update.apk");
                if (file.exists()) file.delete();

                InputStream input = new BufferedInputStream(conn.getInputStream());
                FileOutputStream output = new FileOutputStream(file);

                byte[] data = new byte[4096];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    total += count;
                    if (fileLength > 0) {
                        int prog = (int) (total * 100 / fileLength);
                        runOnUiThread(() -> progress.setProgress(prog));
                    }
                    output.write(data, 0, count);
                }
                output.flush();
                output.close();
                input.close();

                runOnUiThread(() -> {
                    progress.dismiss();
                    installDownloadedApk(file);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.dismiss();
                    Toast.makeText(MainActivity.this, "Download error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void installDownloadedApk(File apkFile) {
        try {
            Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", apkFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(contentUri, "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Failed to start install: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        if (ttsHelper != null) { ttsHelper.stop(); ttsHelper.shutdown(); }
        super.onDestroy();
    }
}
