package com.speechplus.tts;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import com.google.android.material.bottomnavigation.BottomNavigationView;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_VOICE_SEARCH = 101;
    private static final int REQ_RECORD_AUDIO = 102;
    private static final String PREFS_NAME = "SpeechPlusPrefs";

    private SharedPreferences prefs;

    // Search bar views
    private EditText edtSearch;
    private ImageButton btnClearSearch;
    private ImageButton btnVoiceSearch;

    // 5 Tab containers
    private ScrollView tabModes;
    private ScrollView tabLanguages;
    private ScrollView tabVoices;
    private ScrollView tabAdvanced;
    private ScrollView tabMore;
    private BottomNavigationView bottomNav;

    // Tab 1 (Modes) views
    private Spinner spinnerModes;
    private Spinner spinnerEngines;
    private Spinner spinnerSecondaryEngines;
    private LinearLayout layoutSecondary;
    private Button btnTestVoice;
    private Button btnStopSpeaking;

    // Tab 2 (Languages) views
    private Button btnSelectAllLangs;
    private Button btnClearAllLangs;
    private LinearLayout layoutLanguagesList;

    // Tab 3 (Voices) views
    private Spinner spinnerVoiceLanguage;
    private Spinner spinnerVoiceEngine;
    private Spinner spinnerVoiceVariant;
    private TextView lblVoicesRate;
    private SeekBar seekVoiceRate;
    private Button btnRateMinus;
    private Button btnRatePlus;
    private TextView lblVoicesPitch;
    private SeekBar seekVoicePitch;
    private Button btnPitchMinus;
    private Button btnPitchPlus;
    private TextView lblVoicesVolume;
    private SeekBar seekVoiceVolume;
    private Button btnTestVoiceSingle;
    private Button btnDefaultVoice;
    private Button btnSaveVoiceSettings;
    private LinearLayout layoutConfiguredLanguages;

    // Tab 4 (Advanced) views
    private Spinner spinnerAudioRouting;
    private CheckBox chkAmplifyVolume;
    private CheckBox chkKeepAlive;
    private CheckBox chkForceRate;
    private CheckBox chkForcePitch;
    private CheckBox chkStripAttributes;
    private CheckBox chkQuickChar;
    private Button btnIgnoreBattery;

    // Tab 5 (More) views
    private Button btnOpenSystemTts;
    private Button btnCheckUpdateMore;
    private Button btnOpenAboutPage;

    // Engines & Language data
    public static class EngineInfo {
        public String name;
        public String label;
        public EngineInfo(String n, String l) { name = n; label = l; }
        public String toString() { return label; }
    }

    public static class LangItem {
        public String code;
        public String displayName;
        public LangItem(String c, String d) { code = c; displayName = d; }
        public String toString() { return displayName; }
    }

    private List<EngineInfo> installedEngines = new ArrayList<>();
    private List<LangItem> globalLanguages = new ArrayList<>();
    private TextToSpeech previewTts = null;
    private TextToSpeech activeVoiceTestTts = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        showWhatsNewDialogIfNeeded();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        initViews();
        setupSearchAndVoice();
        setupBottomNavigation();
        loadEngines();
        loadGlobalLanguages();

        setupTabModes();
        setupTabLanguages();
        setupTabVoices();
        setupTabAdvanced();
        setupTabMore();
    }

    private void initViews() {
        edtSearch = findViewById(R.id.edtSearch);
        btnClearSearch = findViewById(R.id.btnClearSearch);
        btnVoiceSearch = findViewById(R.id.btnVoiceSearch);

        tabModes = findViewById(R.id.tabModes);
        tabLanguages = findViewById(R.id.tabLanguages);
        tabVoices = findViewById(R.id.tabVoices);
        tabAdvanced = findViewById(R.id.tabAdvanced);
        tabMore = findViewById(R.id.tabMore);
        bottomNav = findViewById(R.id.bottomNavigation);

        // Modes
        spinnerModes = findViewById(R.id.spinnerModes);
        spinnerEngines = findViewById(R.id.spinnerEngines);
        spinnerSecondaryEngines = findViewById(R.id.spinnerSecondaryEngines);
        layoutSecondary = findViewById(R.id.layoutSecondary);
        btnTestVoice = findViewById(R.id.btnTestVoice);
        btnStopSpeaking = findViewById(R.id.btnStopSpeaking);

        // Languages
        btnSelectAllLangs = findViewById(R.id.btnSelectAllLangs);
        btnClearAllLangs = findViewById(R.id.btnClearAllLangs);
        layoutLanguagesList = findViewById(R.id.layoutLanguagesList);

        // Voices
        spinnerVoiceLanguage = findViewById(R.id.spinnerVoiceLanguage);
        spinnerVoiceEngine = findViewById(R.id.spinnerVoiceEngine);
        spinnerVoiceVariant = findViewById(R.id.spinnerVoiceVariant);
        lblVoicesRate = findViewById(R.id.lblVoicesRate);
        seekVoiceRate = findViewById(R.id.seekVoiceRate);
        btnRateMinus = findViewById(R.id.btnRateMinus);
        btnRatePlus = findViewById(R.id.btnRatePlus);
        lblVoicesPitch = findViewById(R.id.lblVoicesPitch);
        seekVoicePitch = findViewById(R.id.seekVoicePitch);
        btnPitchMinus = findViewById(R.id.btnPitchMinus);
        btnPitchPlus = findViewById(R.id.btnPitchPlus);
        lblVoicesVolume = findViewById(R.id.lblVoicesVolume);
        seekVoiceVolume = findViewById(R.id.seekVoiceVolume);
        btnTestVoiceSingle = findViewById(R.id.btnTestVoiceSingle);
        btnDefaultVoice = findViewById(R.id.btnDefaultVoice);
        btnSaveVoiceSettings = findViewById(R.id.btnSaveVoiceSettings);
        layoutConfiguredLanguages = findViewById(R.id.layoutConfiguredLanguages);

        // Advanced
        spinnerAudioRouting = findViewById(R.id.spinnerAudioRouting);
        chkAmplifyVolume = findViewById(R.id.chkAmplifyVolume);
        chkKeepAlive = findViewById(R.id.chkKeepAlive);
        chkForceRate = findViewById(R.id.chkForceRate);
        chkForcePitch = findViewById(R.id.chkForcePitch);
        chkStripAttributes = findViewById(R.id.chkStripAttributes);
        chkQuickChar = findViewById(R.id.chkQuickChar);
        btnIgnoreBattery = findViewById(R.id.btnIgnoreBattery);

        // More
        btnOpenSystemTts = findViewById(R.id.btnOpenSystemTts);
        btnCheckUpdateMore = findViewById(R.id.btnCheckUpdateMore);
        btnOpenAboutPage = findViewById(R.id.btnOpenAboutPage);
    }
    private void setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            tabModes.setVisibility(id == R.id.nav_modes ? View.VISIBLE : View.GONE);
            tabLanguages.setVisibility(id == R.id.nav_languages ? View.VISIBLE : View.GONE);
            tabVoices.setVisibility(id == R.id.nav_voices ? View.VISIBLE : View.GONE);
            tabAdvanced.setVisibility(id == R.id.nav_advanced ? View.VISIBLE : View.GONE);
            tabMore.setVisibility(id == R.id.nav_more ? View.VISIBLE : View.GONE);
            return true;
        });
    }

    private void setupSearchAndVoice() {
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s.toString().trim().toLowerCase(Locale.ROOT);
                btnClearSearch.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                filterCurrentTab(q);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClearSearch.setOnClickListener(v -> {
            edtSearch.setText("");
            filterCurrentTab("");
        });

        btnVoiceSearch.setOnClickListener(v -> startVoiceSearch());
    }

    private void startVoiceSearch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
            return;
        }

        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak language or setting name...");
            startActivityForResult(intent, REQ_VOICE_SEARCH);
        } catch (Exception e) {
            Toast.makeText(this, "Speech recognition not available on this device", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceSearch();
            } else {
                Toast.makeText(this, "Microphone permission is needed for voice search", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VOICE_SEARCH && resultCode == RESULT_OK && data != null) {
            ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (matches != null && !matches.isEmpty()) {
                String spoken = matches.get(0);
                edtSearch.setText(spoken);
                filterCurrentTab(spoken.toLowerCase(Locale.ROOT));
            }
        }
    }

    private void filterCurrentTab(String query) {
        int childCount = layoutLanguagesList.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View v = layoutLanguagesList.getChildAt(i);
            if (v instanceof CheckBox) {
                CheckBox cb = (CheckBox) v;
                String text = cb.getText().toString().toLowerCase(Locale.ROOT);
                cb.setVisibility(query.isEmpty() || text.contains(query) ? View.VISIBLE : View.GONE);
            }
        }

        int confCount = layoutConfiguredLanguages.getChildCount();
        for (int i = 0; i < confCount; i++) {
            View v = layoutConfiguredLanguages.getChildAt(i);
            if (v instanceof TextView) {
                TextView tv = (TextView) v;
                String text = tv.getText().toString().toLowerCase(Locale.ROOT);
                tv.setVisibility(query.isEmpty() || text.contains(query) ? View.VISIBLE : View.GONE);
            }
        }
    }

    private void loadEngines() {
        installedEngines.clear();
        installedEngines.add(new EngineInfo("disabled", "*Disabled"));
        Intent intent = new Intent("android.intent.action.TTS_SERVICE");
        List<ResolveInfo> resolveInfos = getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolveInfos.isEmpty()) {
            resolveInfos = getPackageManager().queryIntentServices(intent, 0);
        }

        for (ResolveInfo ri : resolveInfos) {
            String pkg = ri.serviceInfo != null ? ri.serviceInfo.packageName : (ri.activityInfo != null ? ri.activityInfo.packageName : null);
            if (pkg == null) continue;
            String label = ri.loadLabel(getPackageManager()).toString();
            installedEngines.add(new EngineInfo(pkg, label));
        }

        if (installedEngines.isEmpty()) {
            installedEngines.add(new EngineInfo("com.google.android.tts", "Google Text-to-speech Engine"));
        }
    }

    private void loadGlobalLanguages() {
        globalLanguages.clear();
        globalLanguages.add(new LangItem("ar", "Arabic"));
        globalLanguages.add(new LangItem("as", "Assamese"));
        globalLanguages.add(new LangItem("bn", "Bengali"));
        globalLanguages.add(new LangItem("bho", "Bhojpuri"));
        globalLanguages.add(new LangItem("bg", "Bulgarian"));
        globalLanguages.add(new LangItem("my", "Burmese"));
        globalLanguages.add(new LangItem("zh", "Chinese"));
        globalLanguages.add(new LangItem("cs", "Czech"));
        globalLanguages.add(new LangItem("da", "Danish"));
        globalLanguages.add(new LangItem("nl", "Dutch"));
        globalLanguages.add(new LangItem("en", "English"));
        globalLanguages.add(new LangItem("fi", "Finnish"));
        globalLanguages.add(new LangItem("fr", "French"));
        globalLanguages.add(new LangItem("de", "German"));
        globalLanguages.add(new LangItem("el", "Greek"));
        globalLanguages.add(new LangItem("gu", "Gujarati"));
        globalLanguages.add(new LangItem("he", "Hebrew"));
        globalLanguages.add(new LangItem("hi", "Hindi"));
        globalLanguages.add(new LangItem("hu", "Hungarian"));
        globalLanguages.add(new LangItem("id", "Indonesian"));
        globalLanguages.add(new LangItem("it", "Italian"));
        globalLanguages.add(new LangItem("ja", "Japanese"));
        globalLanguages.add(new LangItem("kn", "Kannada"));
        globalLanguages.add(new LangItem("ko", "Korean"));
        globalLanguages.add(new LangItem("ml", "Malayalam"));
        globalLanguages.add(new LangItem("mr", "Marathi"));
        globalLanguages.add(new LangItem("ne", "Nepali"));
        globalLanguages.add(new LangItem("or", "Odia"));
        globalLanguages.add(new LangItem("fa", "Persian"));
        globalLanguages.add(new LangItem("pl", "Polish"));
        globalLanguages.add(new LangItem("pt", "Portuguese"));
        globalLanguages.add(new LangItem("pa", "Punjabi"));
        globalLanguages.add(new LangItem("ro", "Romanian"));
        globalLanguages.add(new LangItem("ru", "Russian"));
        globalLanguages.add(new LangItem("sa", "Sanskrit"));
        globalLanguages.add(new LangItem("es", "Spanish"));
        globalLanguages.add(new LangItem("sv", "Swedish"));
        globalLanguages.add(new LangItem("ta", "Tamil"));
        globalLanguages.add(new LangItem("te", "Telugu"));
        globalLanguages.add(new LangItem("th", "Thai"));
        globalLanguages.add(new LangItem("tr", "Turkish"));
        globalLanguages.add(new LangItem("uk", "Ukrainian"));
        globalLanguages.add(new LangItem("ur", "Urdu"));
        globalLanguages.add(new LangItem("vi", "Vietnamese"));
    }
    private void setupTabModes() {
        String[] modes = {"Mode 0: Single Engine Mode", "Mode 1: Auto Detect Language Mode", "Mode 2: Mixed Regional / Dual Mode"};
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modes);
        spinnerModes.setAdapter(modeAdapter);
        int savedMode = prefs.getInt("tts_mode", 0);
        spinnerModes.setSelection(Math.min(savedMode, modes.length - 1));

        spinnerModes.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putInt("tts_mode", position).apply();
                layoutSecondary.setVisibility(View.GONE);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        ArrayAdapter<EngineInfo> engAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, installedEngines);
        spinnerEngines.setAdapter(engAdapter);
        spinnerSecondaryEngines.setAdapter(engAdapter);

        String pSaved = prefs.getString("primary_engine", prefs.getString("selected_engine", ""));
        String sSaved = prefs.getString("secondary_engine", "");

        for (int i = 0; i < installedEngines.size(); i++) {
            if (installedEngines.get(i).name.equals(pSaved)) spinnerEngines.setSelection(i);
            if (installedEngines.get(i).name.equals(sSaved)) spinnerSecondaryEngines.setSelection(i);
        }

        spinnerEngines.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String pkg = installedEngines.get(position).name;
                prefs.edit().putString("primary_engine", pkg).putString("selected_engine", pkg).apply();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerSecondaryEngines.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String pkg = installedEngines.get(position).name;
                prefs.edit().putString("secondary_engine", pkg).apply();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        if (btnTestVoice != null) btnTestVoice.setVisibility(android.view.View.GONE);
        btnStopSpeaking.setOnClickListener(v -> stopSpeaking());
    }

    private void setupTabLanguages() {
        layoutLanguagesList.removeAllViews();
        Set<String> enabledLangs = prefs.getStringSet("enabled_languages", null);
        if (enabledLangs == null) {
            enabledLangs = new HashSet<>();
            for (LangItem item : globalLanguages) {
                enabledLangs.add(item.code);
            }
            prefs.edit().putStringSet("enabled_languages", enabledLangs).apply();
        }

        for (LangItem item : globalLanguages) {
            CheckBox cb = new CheckBox(this);
            cb.setText(item.displayName);
            cb.setTextColor(0xFFFFFFFF);
            cb.setTextSize(15f);
            cb.setChecked(enabledLangs.contains(item.code));
            cb.setOnCheckedChangeListener((btn, isChecked) -> {
                Set<String> set = new HashSet<>(prefs.getStringSet("enabled_languages", new HashSet<>()));
                if (isChecked) set.add(item.code);
                else set.remove(item.code);
                prefs.edit().putStringSet("enabled_languages", set).apply();
            });
            layoutLanguagesList.addView(cb);
        }

        btnSelectAllLangs.setOnClickListener(v -> {
            Set<String> all = new HashSet<>();
            int count = layoutLanguagesList.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = layoutLanguagesList.getChildAt(i);
                if (child instanceof CheckBox) {
                    ((CheckBox) child).setChecked(true);
                }
            }
            for (LangItem it : globalLanguages) all.add(it.code);
            prefs.edit().putStringSet("enabled_languages", all).apply();
            Toast.makeText(this, "All languages selected", Toast.LENGTH_SHORT).show();
        });

        btnClearAllLangs.setOnClickListener(v -> {
            int count = layoutLanguagesList.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = layoutLanguagesList.getChildAt(i);
                if (child instanceof CheckBox) {
                    ((CheckBox) child).setChecked(false);
                }
            }
            prefs.edit().putStringSet("enabled_languages", new HashSet<>()).apply();
            Toast.makeText(this, "All languages cleared", Toast.LENGTH_SHORT).show();
        });
    }
    private void setupTabVoices() {
        ArrayAdapter<LangItem> langAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, globalLanguages);
        spinnerVoiceLanguage.setAdapter(langAdapter);

        spinnerVoiceLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < globalLanguages.size()) {
                    loadLanguageVoiceMapping(globalLanguages.get(position).code);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        ArrayAdapter<EngineInfo> engAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, installedEngines);
        spinnerVoiceEngine.setAdapter(engAdapter);

        spinnerVoiceEngine.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                initPreviewEngine(installedEngines.get(position).name);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        seekVoiceRate.setMax(100);
        seekVoiceRate.setProgress(prefs.getInt("voice_tab_rate_progress", 50));
        updateRateLabel();

        seekVoiceRate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                prefs.edit().putInt("voice_tab_rate_progress", progress).apply();
                updateRateLabel();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnRateMinus.setOnClickListener(v -> {
            int p = Math.max(0, seekVoiceRate.getProgress() - 5);
            seekVoiceRate.setProgress(p);
        });

        btnRatePlus.setOnClickListener(v -> {
            int p = Math.min(100, seekVoiceRate.getProgress() + 5);
            seekVoiceRate.setProgress(p);
        });

        seekVoicePitch.setMax(100);
        seekVoicePitch.setProgress(prefs.getInt("voice_tab_pitch_progress", 50));
        updatePitchLabel();

        seekVoicePitch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                prefs.edit().putInt("voice_tab_pitch_progress", progress).apply();
                updatePitchLabel();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnPitchMinus.setOnClickListener(v -> {
            int p = Math.max(0, seekVoicePitch.getProgress() - 5);
            seekVoicePitch.setProgress(p);
        });

        btnPitchPlus.setOnClickListener(v -> {
            int p = Math.min(100, seekVoicePitch.getProgress() + 5);
            seekVoicePitch.setProgress(p);
        });

        seekVoiceVolume.setMax(100);
        seekVoiceVolume.setProgress(prefs.getInt("voice_tab_volume", 100));
        lblVoicesVolume.setText("Voice Volume: " + seekVoiceVolume.getProgress() + "%");
        seekVoiceVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                prefs.edit().putInt("voice_tab_volume", progress).apply();
                lblVoicesVolume.setText("Voice Volume: " + progress + "%");
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnDefaultVoice.setOnClickListener(v -> {
            seekVoiceRate.setProgress(50);
            seekVoicePitch.setProgress(50);
            seekVoiceVolume.setProgress(100);
            Toast.makeText(this, "Voice parameters reset to default 1.0x", Toast.LENGTH_SHORT).show();
        });

        btnTestVoiceSingle.setOnClickListener(v -> {
            LangItem lang = (LangItem) spinnerVoiceLanguage.getSelectedItem();
            EngineInfo eng = (EngineInfo) spinnerVoiceEngine.getSelectedItem();
            if (lang == null || eng == null) return;

            if ("disabled".equals(eng.name)) {
                if (activeVoiceTestTts != null) {
                    try { activeVoiceTestTts.stop(); } catch (Exception ignored) {}
                }
                Toast.makeText(this, "Voice is disabled for " + lang.displayName, Toast.LENGTH_SHORT).show();
                return;
            }

            float rate = 0.5f + (seekVoiceRate.getProgress() / 100.0f) * 1.5f;
            float pitch = 0.5f + (seekVoicePitch.getProgress() / 100.0f) * 1.5f;
            int vol = seekVoiceVolume.getProgress();
            String selVar = spinnerVoiceVariant.getSelectedItem() != null ? spinnerVoiceVariant.getSelectedItem().toString() : "Default Voice";

            testSpeakPerLanguage(lang, eng.name, selVar, rate, pitch, vol);
        });

        btnSaveVoiceSettings.setOnClickListener(v -> saveLanguageVoiceMapping());

        refreshConfiguredLanguagesList();
    }

    private void updateRateLabel() {
        float r = 0.5f + (seekVoiceRate.getProgress() / 100.0f) * 1.5f;
        lblVoicesRate.setText(String.format(Locale.US, "Speech Rate: %.2fx", r));
    }

    private void updatePitchLabel() {
        float p = 0.5f + (seekVoicePitch.getProgress() / 100.0f) * 1.5f;
        lblVoicesPitch.setText(String.format(Locale.US, "Speech Pitch: %.2fx", p));
    }

    private void initPreviewEngine(String pkg) {
        if (previewTts != null) {
            try { previewTts.shutdown(); } catch (Exception ignored) {}
            previewTts = null;
        }

        List<String> variants = new ArrayList<>();
        variants.add("Default Voice");

        if (pkg == null || "disabled".equals(pkg)) {
            ArrayAdapter<String> varAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, variants);
            spinnerVoiceVariant.setAdapter(varAdapter);
            return;
        }

        String pkgLower = pkg.toLowerCase(java.util.Locale.US);
        if (pkgLower.contains("eloquence")) {
            variants.add("Reed");
            variants.add("Shelley");
            variants.add("Bobby");
            variants.add("Rocko");
            variants.add("Glen");
            variants.add("Sandy");
            variants.add("Grandma");
            variants.add("Grandpa");
            variants.add("Junior");
            ArrayAdapter<String> varAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, variants);
            spinnerVoiceVariant.setAdapter(varAdapter);
            return;
        }

        if (pkgLower.contains("smartvoice")) {
            ArrayAdapter<String> varAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, variants);
            spinnerVoiceVariant.setAdapter(varAdapter);
            return;
        }

        final String selectedLangText;
        if (spinnerVoiceLanguage != null && spinnerVoiceLanguage.getSelectedItem() != null) {
            selectedLangText = spinnerVoiceLanguage.getSelectedItem().toString().toLowerCase(java.util.Locale.US).trim();
        } else {
            selectedLangText = "";
        }

        String tempCode = "";
        for (LangItem item : globalLanguages) {
            if (item.displayName.equalsIgnoreCase(selectedLangText) || selectedLangText.startsWith(item.displayName.toLowerCase(java.util.Locale.US))) {
                tempCode = item.code.split("-")[0].toLowerCase(java.util.Locale.US);
                break;
            }
        }
        if (tempCode.isEmpty() && selectedLangText.length() >= 2) {
            tempCode = selectedLangText.substring(0, 2);
        }
        final String targetLangCode = tempCode;

        final TextToSpeech[] holder = new TextToSpeech[1];
        holder[0] = new TextToSpeech(this, status -> {
            TextToSpeech ttsInstance = holder[0] != null ? holder[0] : previewTts;
            if (status == TextToSpeech.SUCCESS && ttsInstance != null) {
                try {
                    Set<Voice> voices = ttsInstance.getVoices();
                    if (voices != null) {
                        for (Voice v : voices) {
                            if (v != null && v.getName() != null) {
                                if (v.getLocale() != null && !targetLangCode.isEmpty()) {
                                    String vl = v.getLocale().getLanguage().toLowerCase(java.util.Locale.US);
                                    if (!vl.equals(targetLangCode)) {
                                        continue;
                                    }
                                }
                                if (!variants.contains(v.getName())) {
                                    variants.add(v.getName());
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
            runOnUiThread(() -> {
                ArrayAdapter<String> varAdapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_spinner_dropdown_item, variants);
                spinnerVoiceVariant.setAdapter(varAdapter);
            });
        }, pkg);
        previewTts = holder[0];
    }
    private void loadLanguageVoiceMapping(String langCode) {
        String mapKey = "lang_map_" + langCode;
        String jsonStr = prefs.getString(mapKey, null);
        if (jsonStr != null) {
            try {
                JSONObject obj = new JSONObject(jsonStr);
                String engPkg = obj.optString("engine_pkg", "disabled");
                float rate = (float) obj.optDouble("rate", 1.0);
                float pitch = (float) obj.optDouble("pitch", 1.0);
                int vol = obj.optInt("volume", 100);

                int engIdx = 0;
                for (int i = 0; i < installedEngines.size(); i++) {
                    if (installedEngines.get(i).name.equals(engPkg)) {
                        engIdx = i;
                        break;
                    }
                }
                spinnerVoiceEngine.setSelection(engIdx);

                int rateProg = Math.max(0, Math.min(100, Math.round((rate - 0.5f) / 1.5f * 100.0f)));
                int pitchProg = Math.max(0, Math.min(100, Math.round((pitch - 0.5f) / 1.5f * 100.0f)));

                seekVoiceRate.setProgress(rateProg);
                seekVoicePitch.setProgress(pitchProg);
                seekVoiceVolume.setProgress(vol);

                updateRateLabel();
                updatePitchLabel();
                lblVoicesVolume.setText("Voice Volume: " + vol + "%");
                return;
            } catch (Exception ignored) {}
        }

        spinnerVoiceEngine.setSelection(0);
        seekVoiceRate.setProgress(50);
        seekVoicePitch.setProgress(50);
        seekVoiceVolume.setProgress(100);
        updateRateLabel();
        updatePitchLabel();
        lblVoicesVolume.setText("Voice Volume: 100%");
    }

    private void saveLanguageVoiceMapping() {
        LangItem selLang = (LangItem) spinnerVoiceLanguage.getSelectedItem();
        EngineInfo selEng = (EngineInfo) spinnerVoiceEngine.getSelectedItem();
        String selVar = spinnerVoiceVariant.getSelectedItem() != null ? spinnerVoiceVariant.getSelectedItem().toString() : "Default";

        if (selLang == null || selEng == null) return;

        float rate = 0.5f + (seekVoiceRate.getProgress() / 100.0f) * 1.5f;
        float pitch = 0.5f + (seekVoicePitch.getProgress() / 100.0f) * 1.5f;
        int vol = seekVoiceVolume.getProgress();

        String mapKey = "lang_map_" + selLang.code;
        JSONObject obj = new JSONObject();
        try {
            obj.put("lang_code", selLang.code);
            obj.put("lang_name", selLang.displayName);
            obj.put("engine_pkg", selEng.name);
            obj.put("engine_label", selEng.label);
            obj.put("voice_variant", selVar);
            obj.put("rate", (double) rate);
            obj.put("pitch", (double) pitch);
            obj.put("volume", vol);
            prefs.edit().putString(mapKey, obj.toString()).apply();

            Set<String> savedKeys = new HashSet<>(prefs.getStringSet("configured_lang_keys", new HashSet<>()));
            savedKeys.add(mapKey);
            prefs.edit().putStringSet("configured_lang_keys", savedKeys).apply();

            Toast.makeText(this, "Settings saved for " + selLang.displayName, Toast.LENGTH_SHORT).show();
            refreshConfiguredLanguagesList();
        } catch (Exception e) {
            Toast.makeText(this, "Error saving settings", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshConfiguredLanguagesList() {
        layoutConfiguredLanguages.removeAllViews();
        Set<String> savedKeys = prefs.getStringSet("configured_lang_keys", new HashSet<>());

        if (savedKeys.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("No specific language mappings saved yet. Global settings apply.");
            tv.setTextColor(0xFF888888);
            tv.setTextSize(13f);
            tv.setPadding(0, 8, 0, 8);
            layoutConfiguredLanguages.addView(tv);
            return;
        }

        for (String k : savedKeys) {
            String jsonStr = prefs.getString(k, "");
            if (jsonStr.isEmpty()) continue;
            try {
                JSONObject o = new JSONObject(jsonStr);
                String lName = o.getString("lang_name");
                String eLabel = o.getString("engine_label");
                double r = o.getDouble("rate");
                double p = o.getDouble("pitch");

                TextView item = new TextView(this);
                item.setText(String.format(Locale.US, "• %s ➔ %s (Rate: %.2fx, Pitch: %.2fx)", lName, eLabel, r, p));
                item.setTextColor(0xFF03DAC5);
                item.setTextSize(14f);
                item.setPadding(0, 6, 0, 6);
                layoutConfiguredLanguages.addView(item);
            } catch (Exception ignored) {}
        }
    }
    private void setupTabAdvanced() {
        String[] streams = {"0: Media Stream (STREAM_MUSIC)", "1: Accessibility Stream (STREAM_ACCESSIBILITY)"};
        ArrayAdapter<String> streamAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, streams);
        spinnerAudioRouting.setAdapter(streamAdapter);
        int savedRouting = prefs.getInt("audio_routing", 0);
        spinnerAudioRouting.setSelection(Math.min(savedRouting, streams.length - 1));

        spinnerAudioRouting.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putInt("audio_routing", position).apply();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        chkAmplifyVolume.setChecked(prefs.getBoolean("amplify_volume", true));
        chkAmplifyVolume.setOnCheckedChangeListener((b, isChecked) -> prefs.edit().putBoolean("amplify_volume", isChecked).apply());

        chkKeepAlive.setChecked(prefs.getBoolean("keep_alive", true));
        chkKeepAlive.setOnCheckedChangeListener((b, isChecked) -> prefs.edit().putBoolean("keep_alive", isChecked).apply());

        chkForceRate.setChecked(prefs.getBoolean("force_rate", true));
        chkForceRate.setOnCheckedChangeListener((b, isChecked) -> prefs.edit().putBoolean("force_rate", isChecked).apply());

        chkForcePitch.setChecked(prefs.getBoolean("force_pitch", true));
        chkForcePitch.setOnCheckedChangeListener((b, isChecked) -> prefs.edit().putBoolean("force_pitch", isChecked).apply());

        chkStripAttributes.setChecked(prefs.getBoolean("strip_attributes", false));
        chkStripAttributes.setOnCheckedChangeListener((b, isChecked) -> prefs.edit().putBoolean("strip_attributes", isChecked).apply());

        chkQuickChar.setChecked(prefs.getBoolean("quick_char", false));
        chkQuickChar.setOnCheckedChangeListener((b, isChecked) -> prefs.edit().putBoolean("quick_char", isChecked).apply());

        btnIgnoreBattery.setOnClickListener(v -> requestIgnoreBatteryOptimization());
    }

    private void requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                try { startActivity(intent); } catch (Exception e1) { try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); } catch (Exception ignored) {} }
            } else {
                Toast.makeText(this, "Battery is already unrestricted!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setupTabMore() {
        btnOpenSystemTts.setOnClickListener(v -> {
            try {
                Intent intent = new Intent("com.android.settings.TTS_SETTINGS");
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Unable to open system TTS settings", Toast.LENGTH_SHORT).show();
            }
        });

        btnCheckUpdateMore.setOnClickListener(v -> checkForUpdates());

        btnOpenAboutPage.setOnClickListener(v -> {
            Intent intent = new Intent(this, AboutActivity.class);
            startActivity(intent);
        });
    }
    private void checkForUpdates() {
        ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Checking for updates...");
        pd.show();

        new Thread(() -> {
            try {
                URL url = new URL("https://api.github.com/repos/nitingautam3824/SpeechPlusTTS/releases/latest");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "SpeechPlusTTS-App");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int responseCode = conn.getResponseCode(); if (responseCode == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    JSONObject json = new JSONObject(sb.toString());
                    String latestTag = json.getString("tag_name").replace("v", "").trim();
                    String currentVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;

                    String apkUrl = "";
                    JSONArray assets = json.getJSONArray("assets");
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject a = assets.getJSONObject(i);
                        if (a.getString("name").endsWith(".apk")) {
                            apkUrl = a.getString("browser_download_url");
                            break;
                        }
                    }

                    final String finalApkUrl = apkUrl;
                    final String fLatest = latestTag;
                    runOnUiThread(() -> {
                        pd.dismiss();
                        if (fLatest.compareTo(currentVersion) > 0 && !finalApkUrl.isEmpty()) {
                            showUpdateDialog(fLatest, finalApkUrl);
                        } else {
                            Toast.makeText(MainActivity.this, "App is up to date (v" + currentVersion + ")", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    runOnUiThread(() -> {
                        pd.dismiss();
                        Toast.makeText(MainActivity.this, "Check update failed: HTTP " + responseCode, Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pd.dismiss();
                    Toast.makeText(MainActivity.this, "Check update error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void showUpdateDialog(String newVer, String apkUrl) {
        new AlertDialog.Builder(this)
                .setTitle("Update Available")
                .setMessage("A new version (v" + newVer + ") is available. Download now?")
                .setPositiveButton("Download", (dialog, which) -> downloadAndInstallApk(apkUrl))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void downloadAndInstallApk(String downloadUrl) {
        ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Downloading update...");
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        pd.setIndeterminate(false);
        pd.setMax(100);
        pd.setCancelable(false);
        pd.show();

        new Thread(() -> {
            try {
                URL url = new URL(downloadUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.connect();
                int fileLength = conn.getContentLength();

                File apkFile = new File(getExternalFilesDir(null), "update.apk");
                if (apkFile.exists()) apkFile.delete();

                InputStream in = conn.getInputStream();
                FileOutputStream out = new FileOutputStream(apkFile);

                byte[] buffer = new byte[4096];
                int total = 0;
                int count;
                while ((count = in.read(buffer)) != -1) {
                    total += count;
                    if (fileLength > 0) {
                        int progress = (int) (total * 100 / fileLength);
                        runOnUiThread(() -> pd.setProgress(progress));
                    }
                    out.write(buffer, 0, count);
                }

                out.flush();
                out.close();
                in.close();

                runOnUiThread(() -> {
                    pd.dismiss();
                    installApk(apkFile);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pd.dismiss();
                    Toast.makeText(MainActivity.this, "Download error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void installApk(File apkFile) {
        try {
            Uri apkUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", apkFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Install failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    private void testSpeakPerLanguage(LangItem lang, String engPkg, String variant, float rate, float pitch, int vol) {
        if (activeVoiceTestTts != null) {
            try { activeVoiceTestTts.stop(); } catch (Exception ignored) {}
        }

        activeVoiceTestTts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS && activeVoiceTestTts != null) {
                try {
                    Locale loc = Locale.forLanguageTag(lang.code);
                    activeVoiceTestTts.setLanguage(loc);
                } catch (Exception ignored) {}

                if (variant != null && !variant.equals("Default Voice") && !variant.equals("Default")) {
                    try {
                        Set<Voice> voices = activeVoiceTestTts.getVoices();
                        if (voices != null) {
                            for (Voice v : voices) {
                                if (v.getName().equals(variant)) {
                                    activeVoiceTestTts.setVoice(v);
                                    break;
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }

                activeVoiceTestTts.setSpeechRate(rate);
                activeVoiceTestTts.setPitch(pitch);

                Bundle params = new Bundle();
                params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, vol / 100.0f);
                String tTxt = "en".equals(lang.code) ? "Speech Plus TTS. High quality zero lag speech synthesis." : ("hi".equals(lang.code) || "hi-IN".equals(lang.code) ? "नमस्ते, यह स्पीच प्लस टीटीएस का आवाज़ परीक्षण है।" : "Testing " + lang.displayName); activeVoiceTestTts.speak(tTxt, TextToSpeech.QUEUE_FLUSH, params, "test_lang_id");
            }
        }, engPkg);
    }

    private void testSpeak(String text) {
        if (activeVoiceTestTts != null) {
            try { activeVoiceTestTts.stop(); } catch (Exception ignored) {}
        }

        String eng = prefs.getString("primary_engine", prefs.getString("selected_engine", ""));
        activeVoiceTestTts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS && activeVoiceTestTts != null) {
                float rate = prefs.getBoolean("force_rate", true) ? prefs.getFloat("rate", 1.0f) : 1.0f;
                float pitch = prefs.getBoolean("force_pitch", true) ? prefs.getFloat("pitch", 1.0f) : 1.0f;
                activeVoiceTestTts.setSpeechRate(rate);
                activeVoiceTestTts.setPitch(pitch);
                activeVoiceTestTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "test_id");
            }
        }, eng.isEmpty() ? null : eng);
    }

    private void stopSpeaking() {
        if (activeVoiceTestTts != null) {
            try { activeVoiceTestTts.stop(); } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (previewTts != null) {
            try { previewTts.shutdown(); } catch (Exception ignored) {}
        }
        if (activeVoiceTestTts != null) {
            try { activeVoiceTestTts.shutdown(); } catch (Exception ignored) {}
        }
    }



    private void showWhatsNewDialogIfNeeded() {
        String currentAppVersion = "1.4.4";
        try {
            currentAppVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {}
        final String appVer = currentAppVersion;

        android.content.SharedPreferences sp = getSharedPreferences("speech_plus_prefs", MODE_PRIVATE);
        String savedVer = sp.getString("whats_new_dismissed_ver", "");
        if (appVer.equals(savedVer)) return;

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 10);

        android.widget.TextView tv = new android.widget.TextView(this);
        tv.setTextSize(14f);
        tv.setTextColor(android.graphics.Color.LTGRAY);
        tv.setText("• Permanent Eloquence Fix: Completely resolved crash across TalkBack and system settings.\n" +
                "• Native Voice Preview: Live test natively speaks in the selected language.\n" +
                "• Clean Global Languages (A-Z): Unified list of all world & Indian languages without duplicate accents.\n" +
                "• Smart Dual Auto-Switch: Real-time zero-lag text script routing between Primary and Secondary engines.\n" +
                "• Accurate Update Checker: Confirms when you are already on the latest release.");
        layout.addView(tv);

        android.widget.CheckBox cb = new android.widget.CheckBox(this);
        cb.setText("Don't show again for this version");
        cb.setTextColor(android.graphics.Color.WHITE);
        layout.addView(cb);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("What's New in Speech Plus TTS v1.4.4")
                .setView(layout)
                .setPositiveButton("GOT IT", (d, w) -> {
                    if (cb.isChecked()) {
                        sp.edit().putString("whats_new_dismissed_ver", appVer).apply();
                    }
                })
                .setCancelable(false)
                .show();
    }
}
