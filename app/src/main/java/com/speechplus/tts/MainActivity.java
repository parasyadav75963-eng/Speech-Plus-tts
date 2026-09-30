package com.speechplus.tts;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
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

public class MainActivity extends AppCompatActivity {

    public static final String CURRENT_VERSION = "1.2.1";
    private static final String REPO_RELEASES_URL = "https://api.github.com/repos/parasyadav75963-eng/Speech-Plus-tts/releases/latest";

    private Spinner spinnerLanguages, spinnerEngines, spinnerVoices;
    private SeekBar seekRate, seekPitch;
    private TextView lblRate, lblPitch;
    private SharedPreferences prefs;

    private final String[] modes = {"Single Language Mode", "Dual Language Mode", "Mix Mode (Auto Detect)"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("speech_plus_prefs", MODE_PRIVATE);

        spinnerLanguages = findViewById(R.id.spinnerLanguages);
        spinnerEngines = findViewById(R.id.spinnerEngines);
        spinnerVoices = findViewById(R.id.spinnerVoices);
        seekRate = findViewById(R.id.seekRate);
        seekPitch = findViewById(R.id.seekPitch);
        lblRate = findViewById(R.id.lblRate);
        lblPitch = findViewById(R.id.lblPitch);

        if (spinnerLanguages != null) {
            ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modes);
            spinnerLanguages.setAdapter(modeAdapter);
            spinnerLanguages.setSelection(prefs.getInt("tts_mode", 0));
            spinnerLanguages.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    prefs.edit().putInt("tts_mode", position).apply();
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        Button btnTest = findViewById(R.id.btnTest);
        if (btnTest != null) {
            btnTest.setOnClickListener(v -> Toast.makeText(this, "Testing Speech Plus TTS...", Toast.LENGTH_SHORT).show());
        }

        Button btnCheckUpdate = findViewById(R.id.btnCheckUpdate);
        if (btnCheckUpdate != null) {
            btnCheckUpdate.setOnClickListener(v -> checkForUpdates(true));
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

                if (conn.getResponseCode() == 200) {
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
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "No updates found or server error (" + conn.getResponseCode() + ")", Toast.LENGTH_SHORT).show());
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
}
