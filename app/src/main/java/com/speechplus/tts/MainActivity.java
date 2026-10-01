package com.speechplus.tts;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private SharedPreferences prefs;
    private Spinner spinnerModes, spinnerEngines, spinnerSecondaryEngines, spinnerAudioRouting;
    private LinearLayout layoutSecondary;
    private CheckBox chkAmplifyVolume, chkKeepAlive, chkForceRate, chkForcePitch;
    private SeekBar seekRate, seekPitch;
    private TextView lblRate, lblPitch;
    private Button btnTestVoice, btnStopSpeaking, btnCheckUpdate, btnMoreOptions;
    private TextToSpeech testTts;
    private List<TextToSpeech.EngineInfo> enginesList = new ArrayList<>();
    private static final String CURRENT_VERSION = "1.3.0";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("SpeechPlusPrefs", MODE_PRIVATE);

        spinnerModes = findViewById(R.id.spinnerModes);
        spinnerEngines = findViewById(R.id.spinnerEngines);
        spinnerSecondaryEngines = findViewById(R.id.spinnerSecondaryEngines);
        spinnerAudioRouting = findViewById(R.id.spinnerAudioRouting);
        layoutSecondary = findViewById(R.id.layoutSecondary);
        chkAmplifyVolume = findViewById(R.id.chkAmplifyVolume);
        chkKeepAlive = findViewById(R.id.chkKeepAlive);
        chkForceRate = findViewById(R.id.chkForceRate);
        chkForcePitch = findViewById(R.id.chkForcePitch);
        seekRate = findViewById(R.id.seekRate);
        seekPitch = findViewById(R.id.seekPitch);
        lblRate = findViewById(R.id.lblRate);
        lblPitch = findViewById(R.id.lblPitch);
        btnTestVoice = findViewById(R.id.btnTestVoice);
        btnStopSpeaking = findViewById(R.id.btnStopSpeaking);
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate);
        btnMoreOptions = findViewById(R.id.btnMoreOptions);

        setupModes();
        setupAudioRouting();
        setupSwitches();
        setupSeekBars();
        loadAvailableEngines();

        btnMoreOptions.setOnClickListener(v -> showMoreOptionsMenu(v));
        btnCheckUpdate.setOnClickListener(v -> checkForUpdatesDirect());
        btnTestVoice.setOnClickListener(v -> testSpeechOutput());
        btnStopSpeaking.setOnClickListener(v -> {
            if (testTts != null) testTts.stop();
        });
    }

    private void setupModes() {
        String[] modes = {"Single Engine Mode", "Dual Language Mode (Latin & Regional)", "Mixed Language Mode (Fast Auto Detect)"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modes);
        spinnerModes.setAdapter(adapter);
        int savedMode = prefs.getInt("tts_mode", 0);
        spinnerModes.setSelection(savedMode);

        spinnerModes.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putInt("tts_mode", position).apply();
                layoutSecondary.setVisibility(position > 0 ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupAudioRouting() {
        String[] streams = {"Media Audio Stream", "Accessibility Audio Stream (TalkBack Priority)"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, streams);
        spinnerAudioRouting.setAdapter(adapter);
        spinnerAudioRouting.setSelection(prefs.getInt("audio_routing", 0));

        spinnerAudioRouting.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putInt("audio_routing", position).apply();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupSwitches() {
        chkAmplifyVolume.setChecked(prefs.getBoolean("amplify_volume", true));
        chkAmplifyVolume.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean("amplify_volume", isChecked).apply();
        });

        chkKeepAlive.setChecked(prefs.getBoolean("keep_alive", true));
        chkKeepAlive.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean("keep_alive", isChecked).apply();
        });
        chkForceRate.setChecked(prefs.getBoolean("force_rate", true));
        chkForceRate.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean("force_rate", isChecked).apply();
        });
        chkForcePitch.setChecked(prefs.getBoolean("force_pitch", true));
        chkForcePitch.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean("force_pitch", isChecked).apply();
        });
    }

    private void setupSeekBars() {
        int rVal = prefs.getInt("rate_progress", 50);
        seekRate.setProgress(rVal);
        float rCalc = 0.5f + (rVal / 50.0f);
        lblRate.setText(String.format("Speech Rate: %.2fx", rCalc));

        seekRate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                float rate = 0.5f + (p / 50.0f);
                lblRate.setText(String.format("Speech Rate: %.2fx", rate));
                prefs.edit().putInt("rate_progress", p).putFloat("rate", rate).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        int pVal = prefs.getInt("pitch_progress", 50);
        seekPitch.setProgress(pVal);
        float pCalc = 0.5f + (pVal / 100.0f);
        lblPitch.setText(String.format("Pitch: %.2fx", pCalc));

        seekPitch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                float pitch = 0.5f + (p / 100.0f);
                lblPitch.setText(String.format("Pitch: %.2fx", pitch));
                prefs.edit().putInt("pitch_progress", p).putFloat("pitch", pitch).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
    }

    private void loadAvailableEngines() {
        testTts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                enginesList = testTts.getEngines();
                List<String> names = new ArrayList<>();
                int savedPIdx = 0;
                int savedSIdx = 0;
                String pSaved = prefs.getString("selected_engine", "");
                String sSaved = prefs.getString("secondary_engine", "");

                for (int i = 0; i < enginesList.size(); i++) {
                    names.add(enginesList.get(i).label);
                    if (enginesList.get(i).name.equals(pSaved)) savedPIdx = i;
                    if (enginesList.get(i).name.equals(sSaved)) savedSIdx = i;
                }

                ArrayAdapter<String> engAdapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_spinner_dropdown_item, names);
                spinnerEngines.setAdapter(engAdapter);
                spinnerSecondaryEngines.setAdapter(engAdapter);

                spinnerEngines.setSelection(savedPIdx);
                spinnerSecondaryEngines.setSelection(savedSIdx);

                spinnerEngines.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                        if (pos < enginesList.size()) {
                            prefs.edit().putString("selected_engine", enginesList.get(pos).name).apply();
                        }
                    }
                    @Override public void onNothingSelected(AdapterView<?> p) {}
                });

                spinnerSecondaryEngines.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                        if (pos < enginesList.size()) {
                            prefs.edit().putString("secondary_engine", enginesList.get(pos).name).apply();
                        }
                    }
                    @Override public void onNothingSelected(AdapterView<?> p) {}
                });
            }
        });
    }

    private void testSpeechOutput() {
        if (testTts != null) {
            float r = prefs.getFloat("rate", 1.0f);
            float p = prefs.getFloat("pitch", 1.0f);
            testTts.setSpeechRate(r);
            testTts.setPitch(p);
            testTts.speak("Speech Plus TTS is fully configured and ready for TalkBack.", TextToSpeech.QUEUE_FLUSH, null, "TestID");
        }
    }

    private void showMoreOptionsMenu(View v) {
        PopupMenu menu = new PopupMenu(this, v);
        menu.getMenu().add("Text-to-Speech Settings");
        menu.getMenu().add("Check for Updates");
        menu.getMenu().add("Telegram Community");
        menu.getMenu().add("About Speech Plus");

        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if (title.equals("Text-to-Speech Settings")) {
                startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
            } else if (title.equals("Check for Updates")) {
                checkForUpdatesDirect();
            } else if (title.equals("Telegram Community")) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/speechplus")));
            } else if (title.equals("About Speech Plus")) {
                new AlertDialog.Builder(this)
                        .setTitle("Speech Plus TTS")
                        .setMessage("Version: " + CURRENT_VERSION + "\nDeveloper: Paras Yadav\nSmart Dual Engine and Audio Boost")
                        .setPositiveButton("OK", null)
                        .show();
            }
            return true;
        });
        menu.show();
    }

    private void checkForUpdatesDirect() {
        ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage("Checking for updates...");
        progress.setCancelable(false);
        progress.show();

        new Thread(() -> {
            try {
                URL url = new URL("https://api.github.com/repos/parasyadav75963-eng/Speech-Plus-tts/releases/latest");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "SpeechPlus-App");
                conn.setConnectTimeout(8000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    JSONObject json = new JSONObject(sb.toString());
                    String latestTag = json.getString("tag_name").replace("v", "").trim();
                    JSONArray assets = json.getJSONArray("assets");
                    String downloadUrl = "";

                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject a = assets.getJSONObject(i);
                        if (a.getString("name").endsWith(".apk")) {
                            downloadUrl = a.getString("browser_download_url");
                            break;
                        }
                    }

                    String finalUrl = downloadUrl;
                    runOnUiThread(() -> {
                        progress.dismiss();
                        if (!latestTag.equals(CURRENT_VERSION) && !finalUrl.isEmpty()) {
                            showUpdatePrompt(latestTag, finalUrl);
                        } else {
                            Toast.makeText(MainActivity.this, "App is up to date (v" + CURRENT_VERSION + ")", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    runOnUiThread(() -> {
                        progress.dismiss();
                        Toast.makeText(MainActivity.this, "Update check failed: Server code " + conn, Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.dismiss();
                    Toast.makeText(MainActivity.this, "Network error checking update", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void showUpdatePrompt(String tag, String dlUrl) {
        new AlertDialog.Builder(this)
                .setTitle("Update Available")
                .setMessage("A new version (v" + tag + ") is available. Download and install now?")
                .setPositiveButton("Download", (dialog, which) -> downloadAndInstallApk(dlUrl))
                .setNegativeButton("Later", null)
                .show();
    }

    private void downloadAndInstallApk(String urlStr) {
        ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage("Downloading update...");
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setCancelable(false);
        progress.show();

        new Thread(() -> {
            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.connect();
                int fileLength = conn.getContentLength();

                InputStream input = conn.getInputStream();
                File file = new File(getExternalFilesDir(null), "update.apk");
                FileOutputStream output = new FileOutputStream(file);

                byte[] data = new byte[4096];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    total += count;
                    if (fileLength > 0) {
                        int p = (int) (total * 100 / fileLength);
                        runOnUiThread(() -> progress.setProgress(p));
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
        if (testTts != null) {
            testTts.stop();
            testTts.shutdown();
        }
        super.onDestroy();
    }
}
