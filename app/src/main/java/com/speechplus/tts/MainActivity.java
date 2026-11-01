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
    private android.widget.ImageButton btnNotificationCenter;

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
    
    private CheckBox chkForceRate;
    private CheckBox chkForcePitch;
    private CheckBox chkStripAttributes;
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
        fetchFirebaseNotifications();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupTabs();
        setupAudioGeneratorHub();
        setupSmartAnnouncerUI();
        showITFBWelcomeScreen();

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
        initNotificationCenter();

        tabModes = findViewById(R.id.tabModes);
        tabLanguages = findViewById(R.id.tabLanguages);
        tabVoices = findViewById(R.id.tabVoices);
        tabAdvanced = findViewById(R.id.tabAdvanced);
        tabMore = findViewById(R.id.tabMore);
        bottomNav = findViewById(R.id.bottomNavigation);

        // Modes
        
        

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
        
        chkForceRate = findViewById(R.id.chkForceRate);
        chkForcePitch = findViewById(R.id.chkForcePitch);
        chkStripAttributes = findViewById(R.id.chkStripAttributes);
        btnIgnoreBattery = findViewById(R.id.btnIgnoreBattery);

        // More
        btnOpenSystemTts = findViewById(R.id.btnOpenSystemTts);
        btnCheckUpdateMore = findViewById(R.id.btnCheckUpdateMore);
        // UI_SPINNERS_START
        if (btnCheckUpdateMore != null) {
            btnCheckUpdateMore.setVisibility(android.view.View.GONE);
        }
        setupAdvancedSpinnersAndAppInfo();
        // UI_SPINNERS_END

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
        String[] langs = {"af-ZA,Afrikaans", "sq-AL,Albanian", "am-ET,Amharic", "ar-SA,Arabic (Saudi Arabia)", "ar-AE,Arabic (UAE)", "hy-AM,Armenian", "as-IN,Assamese (India)", "az-AZ,Azerbaijani", "eu-ES,Basque", "be-BY,Belarusian", "bn-IN,Bengali (India)", "bn-BD,Bengali (Bangladesh)", "bs-BA,Bosnian", "bg-BG,Bulgarian", "my-MM,Burmese", "ca-ES,Catalan", "zh-CN,Chinese (Mandarin)", "zh-TW,Chinese (Taiwan)", "zh-HK,Chinese (Cantonese)", "hr-HR,Croatian", "cs-CZ,Czech", "da-DK,Danish", "nl-NL,Dutch", "nl-BE,Dutch (Belgium)", "en-US,English (US)", "en-GB,English (UK)", "en-IN,English (India)", "en-AU,English (Australia)", "en-CA,English (Canada)", "et-EE,Estonian", "fil-PH,Filipino", "fi-FI,Finnish", "fr-FR,French", "fr-CA,French (Canada)", "gl-ES,Galician", "ka-GE,Georgian", "de-DE,German", "el-GR,Greek", "gu-IN,Gujarati (India)", "he-IL,Hebrew", "hi-IN,Hindi (India)", "hu-HU,Hungarian", "is-IS,Icelandic", "id-ID,Indonesian", "it-IT,Italian", "ja-JP,Japanese", "jv-ID,Javanese", "kn-IN,Kannada (India)", "kk-KZ,Kazakh", "km-KH,Khmer", "ko-KR,Korean", "lo-LA,Lao", "lv-LV,Latvian", "lt-LT,Lithuanian", "mk-MK,Macedonian", "ms-MY,Malay", "ml-IN,Malayalam (India)", "mr-IN,Marathi (India)", "mn-MN,Mongolian", "ne-NP,Nepali", "no-NO,Norwegian", "or-IN,Odia (India)", "ps-AF,Pashto", "fa-IR,Persian", "pl-PL,Polish", "pt-BR,Portuguese (Brazil)", "pt-PT,Portuguese (Portugal)", "pa-IN,Punjabi (India)", "ro-RO,Romanian", "ru-RU,Russian", "sr-RS,Serbian", "si-LK,Sinhala", "sk-SK,Slovak", "sl-SI,Slovenian", "es-ES,Spanish", "es-US,Spanish (US)", "sw-KE,Swahili", "sv-SE,Swedish", "ta-IN,Tamil (India)", "te-IN,Telugu (India)", "th-TH,Thai", "tr-TR,Turkish", "uk-UA,Ukrainian", "ur-PK,Urdu", "uz-UZ,Uzbek", "vi-VN,Vietnamese", "cy-GB,Welsh", "zu-ZA,Zulu"};
        for (String l : langs) { String[] p = l.split(","); globalLanguages.add(new LangItem(p[0], p[1])); }
        java.util.Collections.sort(globalLanguages, (a, b) -> a.displayName.compareTo(b.displayName));
    }
    private void setupTabModes() {
        android.widget.RadioGroup rg = findViewById(R.id.radioGroupModes);
        int savedM = prefs.getInt("tts_mode", 0);
        if(savedM == 0) ((android.widget.RadioButton)findViewById(R.id.radioMode0)).setChecked(true);
        else if(savedM == 1) ((android.widget.RadioButton)findViewById(R.id.radioMode1)).setChecked(true);
        else ((android.widget.RadioButton)findViewById(R.id.radioMode2)).setChecked(true);
        rg.setOnCheckedChangeListener((group, checkedId) -> {
            int m = 0;
            if(checkedId == R.id.radioMode1) m = 1;
            else if(checkedId == R.id.radioMode2) m = 2;
            prefs.edit().putInt("tts_mode", m).apply();
        });
        

        
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
            seekVoiceRate.setProgress(20);
            seekVoicePitch.setProgress(20);
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

            float rate = 0.5f + (seekVoiceRate.getProgress() / 50.0f) * 1.5f;
            float pitch = 0.5f + (seekVoicePitch.getProgress() / 50.0f) * 1.5f;
            int vol = seekVoiceVolume.getProgress();
            String selVar = spinnerVoiceVariant.getSelectedItem() != null ? spinnerVoiceVariant.getSelectedItem().toString() : "Default Voice";

            testSpeakPerLanguage(lang, eng.name, selVar, rate, pitch, vol);
        });

        btnSaveVoiceSettings.setOnClickListener(v -> saveLanguageVoiceMapping());

        refreshConfiguredLanguagesList();
    }

    private void updateRateLabel() {
        float r = seekVoiceRate.getProgress() / 50.0f;
        lblVoicesRate.setText(String.format(Locale.US, "Speech Rate: %.2fx", r));
    }

    private void updatePitchLabel() {
        float p = seekVoicePitch.getProgress() / 50.0f;
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
    //             runOnUiThread(() -> {
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
        seekVoiceRate.setProgress(20);
        seekVoicePitch.setProgress(20);
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

        float rate = 0.5f + (seekVoiceRate.getProgress() / 50.0f) * 1.5f;
        float pitch = 0.5f + (seekVoicePitch.getProgress() / 50.0f) * 1.5f;
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
        

        chkForceRate.setChecked(prefs.getBoolean("force_rate", true));
        chkForceRate.setOnCheckedChangeListener((b, isChecked) -> prefs.edit().putBoolean("force_rate", isChecked).apply());

        chkForcePitch.setChecked(prefs.getBoolean("force_pitch", true));
        chkForcePitch.setOnCheckedChangeListener((b, isChecked) -> prefs.edit().putBoolean("force_pitch", isChecked).apply());

        chkStripAttributes.setChecked(prefs.getBoolean("strip_attributes", false));
        chkStripAttributes.setOnCheckedChangeListener((b, isChecked) -> prefs.edit().putBoolean("strip_attributes", isChecked).apply());

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
                URL url = new URL("https://api.github.com/repos/parasyadav75963-eng/Speech-Plus-tts/releases/latest");
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
    //                     runOnUiThread(() -> {
                        pd.dismiss();
                        if (fLatest.compareTo(currentVersion) > 0 && !finalApkUrl.isEmpty()) {
                            showUpdateDialog(fLatest, finalApkUrl);
                        } else {
                            Toast.makeText(MainActivity.this, "App is up to date (v" + currentVersion + ")", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
    //                     runOnUiThread(() -> {
                        pd.dismiss();
                        Toast.makeText(MainActivity.this, "Check update failed: HTTP " + responseCode, Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
    //                 runOnUiThread(() -> {
                    pd.dismiss();
                    Toast.makeText(MainActivity.this, "Check update error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

        private void showUpdateDialog(String newVer, String apkUrl) {
        String currentVer = "3.1.0";
        try {
            currentVer = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {}
        if (!isRemoteVersionNewer(newVer, currentVer)) {
            new AlertDialog.Builder(this)
                    .setTitle("Latest Version Installed")
                    .setMessage("You are already using the latest version of Speech Plus TTS (v" + currentVer + "). No update is required.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
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
    //                         runOnUiThread(() -> pd.setProgress(progress));
                    }
                    out.write(buffer, 0, count);
                }

                out.flush();
                out.close();
                in.close();

    //                 runOnUiThread(() -> {
                    pd.dismiss();
                    installApk(apkFile);
                });
            } catch (Exception e) {
    //                 runOnUiThread(() -> {
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

    @Override
    protected void onDestroy() {
        if(audioMediaPlayer != null) audioMediaPlayer.release();
        if(audioGenTts != null) audioGenTts.shutdown();
        super.onDestroy();
        if (previewTts != null) {
            try { previewTts.shutdown(); } catch (Exception ignored) {}
        }
        if (activeVoiceTestTts != null) {
            try { activeVoiceTestTts.shutdown(); } catch (Exception ignored) {}
        }
    }



        private void showWhatsNewDialogIfNeeded() {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::showItfbWelcomeAndScanner, 250);
    }

    // STARTUP_SCANNER_METHODS_START
    private boolean hasShownSessionIntro = false;

    private android.widget.ArrayAdapter<String> createWhiteSpinnerAdapter(String[] items) {
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            @Override
            public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                android.view.View v = super.getView(position, convertView, parent);
                if (v instanceof android.widget.TextView) {
                    ((android.widget.TextView) v).setTextColor(android.graphics.Color.WHITE);
                    ((android.widget.TextView) v).setTextSize(15f);
                    v.setPadding(20, 20, 20, 20);
                }
                return v;
            }
            @Override
            public android.view.View getDropDownView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                android.view.View v = super.getDropDownView(position, convertView, parent);
                if (v instanceof android.widget.TextView) {
                    ((android.widget.TextView) v).setTextColor(android.graphics.Color.WHITE);
                    ((android.widget.TextView) v).setBackgroundColor(android.graphics.Color.parseColor("#2C2C2C"));
                    ((android.widget.TextView) v).setTextSize(15f);
                    v.setPadding(28, 28, 28, 28);
                }
                return v;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private void setupAdvancedSpinnersAndAppInfo() {
        android.widget.Spinner spinnerPunctuation = findViewById(R.id.spinnerPunctuation);
        if (spinnerPunctuation != null) {
            String[] punctOptions = {
                "Some (Essential Symbols - Default)",
                "Most (Common Symbols & Punctuation)",
                "All (Every Punctuation & Symbol)",
                "None (Ignore All Symbols)"
            };
            spinnerPunctuation.setAdapter(createWhiteSpinnerAdapter(punctOptions));
            spinnerPunctuation.setSelection(prefs.getInt("punctuation_level", 0));
            spinnerPunctuation.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(android.widget.AdapterView<?> p, android.view.View v, int pos, long id) {
                    prefs.edit().putInt("punctuation_level", pos).apply();
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
            });
        }

        android.widget.Spinner spinnerNumLang = findViewById(R.id.spinnerNumLang);
        if (spinnerNumLang != null) {
            String[] langOptions = {
                "Default (Match Sentence Language)",
                "English (Always Read Numbers in English)",
                "Hindi (Always Read Numbers in Hindi)"
            };
            spinnerNumLang.setAdapter(createWhiteSpinnerAdapter(langOptions));
            spinnerNumLang.setSelection(prefs.getInt("num_lang", 0));
            spinnerNumLang.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(android.widget.AdapterView<?> p, android.view.View v, int pos, long id) {
                    prefs.edit().putInt("num_lang", pos).apply();
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
            });
        }

            String[] formatOptions = {
                "Default (Standard Full Number)",
                "Digit by Digit (Single Digits: 1 2 3 4)",
                "Pairs (Two-Digit Groups: 12 34)",
                "Triplets (Three-Digit Groups: 123 4)"
            };
                @Override public void onItemSelected(android.widget.AdapterView<?> p, android.view.View v, int pos, long id) {
                    prefs.edit().putInt("num_format", pos).apply();
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
            });
        }

        
    }

    private void showAppInfoDialog() {
        String infoMessage =
            "App Name: Speech Plus TTS\n" +
            "Version Name: v3.1.0 (Build 31)\n" +
            "Last Updated: November 2026\n" +
            "Developer: Paras Yadav (ITFB Community)\n\n" +
            "Why Speech Plus TTS Was Built:\n" +
            "Speech Plus TTS was created with a dedicated Zero-Lag mission for blind and visually impaired Android users. It solves critical screen-reader challenges including speech latency, dual-voice overlapping, clipping of short words/spellings, and sudden stops during long paragraphs.\n\n" +
            "Latest Core Features in v3.1.0:\n" +
            "• 100% Accessibility Volume Direct PCM Audio Streaming\n" +
            "• Instant Unicode Script Detection (Hindi, English & Indian Regional Languages)\n" +
            "• Non-Overlapping Dual & Auto Multi-Language TTS Switching\n" +
            "• Advanced Punctuation Level Control (Some, Most, All, None)\n" +
            "• Custom Number Reading Language & Digit Formatting (Single Digits, Pairs, Triplets)\n" +
            "• Lead-in Warmup & Tail Silence Guard for Short Words\n" +
            "• Smart Sentence Splitter & Extended Timeout for Long Texts";

        new AlertDialog.Builder(this)
            .setTitle("App Info - Speech Plus TTS v3.1.0")
            .setMessage(infoMessage)
            .setPositiveButton("CLOSE", null)
            .show();
    }

    private void showItfbWelcomeAndScanner() {
        if (hasShownSessionIntro || isFinishing()) return;
        hasShownSessionIntro = true;

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setPadding(50, 45, 50, 35);
        root.setBackgroundColor(android.graphics.Color.parseColor("#1E1E1E"));
        scroll.addView(root);

        android.widget.TextView titleView = new android.widget.TextView(this);
        titleView.setText("Welcome to Speech Plus TTS (ITFB Community)");
        titleView.setTextColor(android.graphics.Color.parseColor("#00E676"));
        titleView.setTextSize(19f);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setPadding(0, 0, 0, 24);
        root.addView(titleView);

        android.widget.TextView descView = new android.widget.TextView(this);
        descView.setText(
            "About Interested Technology For Blind (ITFB) & Speech Plus TTS:\n\n" +
            "Speech Plus TTS is an ultra-fast, accessible Text-to-Speech engine built with a Zero-Lag mission for blind and visually impaired users, developed by Paras Yadav in collaboration with Interested Technology For Blind (ITFB).\n\n" +
            "Core Features of This App:\n" +
            "• Zero-Lag Instant Response: Pre-warmed direct PCM streaming for fast screen navigation.\n" +
            "• Seamless Multi-Language Switching: Reads Hindi, English, and regional scripts accurately without two voices speaking at once.\n" +
            "• Customizable Punctuation & Numbers: Full control over symbol levels, number language, and digit-by-digit reading in the Advanced tab.\n" +
            "• Crystal Clear Short & Long Text Reading: Protects single letters from clipping and reads long articles without stopping.\n\n" +
            "Tap 'CONTINUE WITH APP' below to automatically scan your device's installed TTS engines, languages, and voices."
        );
        descView.setTextColor(android.graphics.Color.WHITE);
        descView.setTextSize(14.5f);
        descView.setLineSpacing(6f, 1.1f);
        descView.setPadding(0, 0, 0, 36);
        root.addView(descView);

        android.widget.Button btnContinue = new android.widget.Button(this);
        btnContinue.setText("CONTINUE WITH APP");
        btnContinue.setTextColor(android.graphics.Color.WHITE);
        btnContinue.setTextSize(16f);
        btnContinue.setAllCaps(true);
        btnContinue.setBackgroundColor(android.graphics.Color.parseColor("#6200EE"));
        btnContinue.setPadding(24, 28, 24, 28);
        root.addView(btnContinue);

        AlertDialog welcomeDialog = new AlertDialog.Builder(this)
            .setView(scroll)
            .setCancelable(false)
            .create();

        btnContinue.setOnClickListener(v -> {
            welcomeDialog.dismiss();
            runAutoTtsDeviceLanguageAndVoiceScanner();
        });

        welcomeDialog.show();
    }

    private void runAutoTtsDeviceLanguageAndVoiceScanner() {
        if (isFinishing()) return;

        android.widget.LinearLayout scanLayout = new android.widget.LinearLayout(this);
        scanLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        scanLayout.setPadding(50, 45, 50, 45);
        scanLayout.setBackgroundColor(android.graphics.Color.parseColor("#1E1E1E"));

        android.widget.TextView scanTitle = new android.widget.TextView(this);
        scanTitle.setText("Scan in Progress...");
        scanTitle.setTextColor(android.graphics.Color.parseColor("#00E676"));
        scanTitle.setTextSize(18f);
        scanTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        scanTitle.setPadding(0, 0, 0, 20);
        scanLayout.addView(scanTitle);

        android.widget.ProgressBar progressBar = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(false);
        progressBar.setMax(100);
    //         progressBar.setProgress(10);
        progressBar.setPadding(0, 10, 0, 24);
        scanLayout.addView(progressBar);

        android.widget.TextView scanStatus = new android.widget.TextView(this);
        String initialMsg = "Scan in progress... Scanning available device languages and voices. Please wait.";
        scanStatus.setText(initialMsg);
        scanStatus.setTextColor(android.graphics.Color.WHITE);
        scanStatus.setTextSize(15f);
        scanLayout.addView(scanStatus);

        AlertDialog scanDialog = new AlertDialog.Builder(this)
            .setView(scanLayout)
            .setCancelable(false)
            .create();

        scanDialog.show();
        scanLayout.announceForAccessibility(initialMsg);

        new Thread(() -> {
            int engineCount = 0;
            java.util.Set<String> discoveredLangs = new java.util.HashSet<>();
            int voiceCount = 0;

            try {
                android.content.Intent intent = new android.content.Intent(android.speech.tts.TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE);
                java.util.List<android.content.pm.ResolveInfo> engines = getPackageManager().queryIntentServices(intent, 0);
                if (engines != null && !engines.isEmpty()) {
                    int total = engines.size();
                    int idx = 0;
                    for (android.content.pm.ResolveInfo info : engines) {
                        idx++;
                        String pkg = info.serviceInfo.packageName;
                        if (getPackageName().equals(pkg)) continue;
                        engineCount++;
                        CharSequence labelCs = info.loadLabel(getPackageManager());
                        String label = labelCs != null ? labelCs.toString() : pkg;
                        final int prog = Math.min(95, (idx * 100) / Math.max(1, total));

    //                         runOnUiThread(() -> {
    //                             progressBar.setProgress(prog);
                            String stepMsg = "Scan in progress (" + prog + "%)... Checking TTS Engine: " + label;
                            scanStatus.setText(stepMsg);
                        });

                        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                        final android.speech.tts.TextToSpeech[] holder = new android.speech.tts.TextToSpeech[1];
                        holder[0] = new android.speech.tts.TextToSpeech(getApplicationContext(), status -> latch.countDown(), pkg);

                        try { latch.await(1800, java.util.concurrent.TimeUnit.MILLISECONDS); } catch (Exception ignored) {}

                        if (holder[0] != null) {
                            try {
                                java.util.Set<android.speech.tts.Voice> voices = holder[0].getVoices();
                                if (voices != null && !voices.isEmpty()) {
                                    for (android.speech.tts.Voice v : voices) {
                                        voiceCount++;
                                        if (v.getLocale() != null) {
                                            discoveredLangs.add(v.getLocale().getLanguage());
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}
                            try { holder[0].shutdown(); } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (Exception ignored) {}

    //             int finalEngines = engineCount;
    //     //             int finalLangs = Math.max(discoveredLangs.size(), 2);
    //             final int finalVoices = Math.max(voiceCount, 2);

            prefs.edit().putInt("scanned_engines_count", finalEngines).putInt("scanned_langs_count", finalLangs).putInt("scanned_voices_count", finalVoices).apply();
    //             runOnUiThread(() -> progressBar.setProgress(100));
            try { Thread.sleep(400); } catch (Exception ignored) {}

    //             runOnUiThread(() -> {
                if (!isFinishing() && scanDialog.isShowing()) scanDialog.dismiss();
                String doneMsg = "Scan Complete! Found " + finalEngines + " TTS Engines, " + finalLangs + " Languages, and " + finalVoices + " Voices.";
                android.widget.Toast.makeText(MainActivity.this, doneMsg, android.widget.Toast.LENGTH_LONG).show();
                getWindow().getDecorView().announceForAccessibility(doneMsg);
            });
        }).start();
    }

    private boolean isRemoteVersionNewer(String remoteVer, String localVer) {
        if (remoteVer == null || localVer == null) return false;
        String r = remoteVer.replaceAll("[^0-9.]", "");
        String l = localVer.replaceAll("[^0-9.]", "");
        if (r.isEmpty() || l.isEmpty()) return false;
        String[] rParts = r.split("\\.");
        String[] lParts = l.split("\\.");
        int len = Math.max(rParts.length, lParts.length);
        for (int i = 0; i < len; i++) {
            int rv = i < rParts.length && !rParts[i].isEmpty() ? Integer.parseInt(rParts[i]) : 0;
            int lv = i < lParts.length && !lParts[i].isEmpty() ? Integer.parseInt(lParts[i]) : 0;
            if (rv > lv) return true;
            if (rv < lv) return false;
        }
        return false;
    }
    // STARTUP_SCANNER_METHODS_END
        // NOTIFICATION_CENTER_START
    private void initNotificationCenter() {
        
    }

    private org.json.JSONArray getStoredNotifications() {
        String jsonStr = prefs.getFloat("app_notifs_ver", 0) > 0 ? prefs.getString("app_notifications_list", "[]") : "[]";
        try {
            return new org.json.JSONArray(jsonStr);
        } catch (Exception e) {
            return new org.json.JSONArray();
        }
    }

    private void saveStoredNotifications(org.json.JSONArray arr) {
        prefs.edit().putString("app_notifications_list", arr.toString()).putFloat("app_notifs_ver", System.currentTimeMillis()).apply();
        updateNotificationBellState();
    }

    private void updateNotificationBellState() {
        if (btnNotificationCenter == null) return;
        org.json.JSONArray arr = getStoredNotifications();
        int unreadCount = 0;
        try {
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject obj = arr.getJSONObject(i);
                if (!obj.optBoolean("read", false)) {
                    unreadCount++;
                }
            }
        } catch (Exception ignored) {}

        final int finalUnread = unreadCount;
    //         runOnUiThread(() -> {
            String desc = "Notifications button, " + finalUnread + " unread notification" + (finalUnread == 1 ? "" : "s") + ". Double tap to view.";
            btnNotificationCenter.setContentDescription(desc);
            try {
                if (finalUnread > 0) {
                    btnNotificationCenter.setColorFilter(android.graphics.Color.parseColor("#00E676"));
                } else {
                    btnNotificationCenter.setColorFilter(android.graphics.Color.WHITE);
                }
            } catch (Exception ignored) {}
        });
    }

    private void showNotificationCenterDialog() {
        org.json.JSONArray arr = getStoredNotifications();
        android.widget.LinearLayout listLayout = new android.widget.LinearLayout(this);
        listLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        listLayout.setPadding(40, 35, 40, 35);
        listLayout.setBackgroundColor(android.graphics.Color.parseColor("#1E1E1E"));

        android.widget.TextView title = new android.widget.TextView(this);
        title.setText("ITFB & App Notifications (" + arr.length() + ")");
        title.setTextColor(android.graphics.Color.parseColor("#00E676"));
        title.setTextSize(18f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, 20);
        listLayout.addView(title);

        if (arr.length() == 0) {
            android.widget.TextView empty = new android.widget.TextView(this);
            empty.setText("No notifications available right now.\nWhen ITFB or developer sends updates, they will appear here.");
            empty.setTextColor(android.graphics.Color.WHITE);
            empty.setTextSize(14.5f);
            empty.setPadding(0, 10, 0, 20);
            listLayout.addView(empty);
        } else {
            android.widget.ScrollView scroll = new android.widget.ScrollView(this);
            android.widget.LinearLayout innerList = new android.widget.LinearLayout(this);
            innerList.setOrientation(android.widget.LinearLayout.VERTICAL);

            try {
                for (int i = 0; i < arr.length(); i++) {
                    final int index = i;
                    org.json.JSONObject obj = arr.getJSONObject(i);
                    String notifTitle = obj.optString("title", "Update");
                    String notifMsg = obj.optString("message", "");
                    boolean isRead = obj.optBoolean("read", false);

                    android.widget.LinearLayout itemCard = new android.widget.LinearLayout(this);
                    itemCard.setOrientation(android.widget.LinearLayout.VERTICAL);
                    itemCard.setBackgroundColor(android.graphics.Color.parseColor(isRead ? "#282828" : "#383838"));
                    itemCard.setPadding(24, 20, 24, 20);
                    android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    lp.setMargins(0, 0, 0, 16);
                    itemCard.setLayoutParams(lp);

                    android.widget.TextView tvTitle = new android.widget.TextView(this);
                    tvTitle.setText((isRead ? "[Read] " : "[New] ") + notifTitle);
                    tvTitle.setTextColor(isRead ? android.graphics.Color.parseColor("#B0BEC5") : android.graphics.Color.parseColor("#00E676"));
                    tvTitle.setTextSize(15f);
                    tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                    itemCard.addView(tvTitle);

                    android.widget.TextView tvMsg = new android.widget.TextView(this);
                    tvMsg.setText(notifMsg);
                    tvMsg.setTextColor(android.graphics.Color.WHITE);
                    tvMsg.setTextSize(14f);
                    tvMsg.setPadding(0, 6, 0, 14);
                    itemCard.addView(tvMsg);

                    android.widget.LinearLayout btnRow = new android.widget.LinearLayout(this);
                    btnRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);

                    android.widget.Button btnToggleRead = new android.widget.Button(this);
                    btnToggleRead.setText(isRead ? "Mark Unread" : "Mark Read");
                    btnToggleRead.setTextSize(12f);
                    btnToggleRead.setTextColor(android.graphics.Color.WHITE);
                    btnToggleRead.setBackgroundColor(android.graphics.Color.parseColor("#455A64"));
                    btnToggleRead.setPadding(16, 10, 16, 10);
                    btnToggleRead.setOnClickListener(v -> {
                        try {
                            obj.put("read", !isRead);
                            saveStoredNotifications(arr);
                            showNotificationCenterDialog();
                        } catch (Exception ignored) {}
                    });
                    btnRow.addView(btnToggleRead);

                    android.widget.Button btnDelete = new android.widget.Button(this);
                    btnDelete.setText("Delete");
                    btnDelete.setTextSize(12f);
                    btnDelete.setTextColor(android.graphics.Color.WHITE);
                    btnDelete.setBackgroundColor(android.graphics.Color.parseColor("#D32F2F"));
                    android.widget.LinearLayout.LayoutParams lpDel = new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    lpDel.setMargins(16, 0, 0, 0);
                    btnDelete.setLayoutParams(lpDel);
                    btnDelete.setPadding(16, 10, 16, 10);
                    btnDelete.setOnClickListener(v -> {
                        try {
                            org.json.JSONArray newArr = new org.json.JSONArray();
                            for (int j = 0; j < arr.length(); j++) {
                                if (j != index) newArr.put(arr.getJSONObject(j));
                            }
                            saveStoredNotifications(newArr);
                            showNotificationCenterDialog();
                        } catch (Exception ignored) {}
                    });
                    btnRow.addView(btnDelete);

                    itemCard.addView(btnRow);
                    innerList.addView(itemCard);
                }
            } catch (Exception ignored) {}

            scroll.addView(innerList);
            android.widget.LinearLayout.LayoutParams scrollLp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 800
            );
            scroll.setLayoutParams(scrollLp);
            listLayout.addView(scroll);
        }

        android.app.AlertDialog notifDialog = new android.app.AlertDialog.Builder(this)
            .setView(listLayout)
            .setPositiveButton("CLOSE", null)
            .setNeutralButton("CLEAR ALL", (d, w) -> {
                saveStoredNotifications(new org.json.JSONArray());
            })
            .create();

        notifDialog.show();
    }
    // NOTIFICATION_CENTER_END


    private void showITFBWelcomeScreen() {
        android.content.SharedPreferences prefs = getSharedPreferences("speechplus_prefs", MODE_PRIVATE);
        boolean isFirstRun = prefs.getBoolean("is_first_run_v4", true);
        if (isFirstRun) {
            new android.app.AlertDialog.Builder(this)
                .setTitle("Welcome to Speech Plus TTS v4.0")
                .setMessage("ITFB द्वारा विकसित! अब नए होम टैब, स्मार्ट एनाउंसर और ज़ीरो-लैग इंजन के साथ।")
                .setPositiveButton("Let's Start", (dialog, which) -> {
                    prefs.edit().putBoolean("is_first_run_v4", false).apply();
                })
                .setCancelable(false)
                .show();
        }
    }


    private java.util.List<String> fetchDeviceTTSLanguages(android.speech.tts.TextToSpeech ttsEngine) {
        java.util.List<String> langList = new java.util.ArrayList<>();
        if (ttsEngine != null) {
            try {
                java.util.Set<java.util.Locale> locales = ttsEngine.getAvailableLanguages();
                if (locales != null) {
                    for (java.util.Locale locale : locales) {
                        langList.add(locale.getDisplayName());
                    }
                    java.util.Collections.sort(langList);
                }
            } catch (Exception e) {
                langList.add("System Default");
            }
        }
        if (langList.isEmpty()) {
            langList.add("English");
            langList.add("Hindi");
        }
        return langList;
    }


    private void applyZeroLagOptimizations(android.speech.tts.TextToSpeech ttsEngine) {
        if (ttsEngine != null) {
            try {
                android.media.AudioManager am = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    am.requestAudioFocus(null, android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
                }
                // Pre-warm the TTS engine to eliminate latency
                ttsEngine.setPitch(1.0f);
                ttsEngine.setSpeechRate(1.0f);
            } catch (Exception ignored) {}
        }
    }


    private void fetchFirebaseNotifications() {
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL("https://speechplustts-default-rtdb.firebaseio.com/notifications.json");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) response.append(line);
                in.close();

                String jsonStr = response.toString();
                if (jsonStr == null || jsonStr.equals("null")) return;

                org.json.JSONArray newNotifs = new org.json.JSONArray();
                if (jsonStr.trim().startsWith("{")) {
                    org.json.JSONObject obj = new org.json.JSONObject(jsonStr);
                    java.util.Iterator<String> keys = obj.keys();
                    while (keys.hasNext()) {
                        newNotifs.put(obj.getJSONObject(keys.next()));
                    }
                } else if (jsonStr.trim().startsWith("[")) {
                    newNotifs = new org.json.JSONArray(jsonStr);
                }

                saveStoredNotifications(newNotifs);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }


    private void setupTabs() {
        int[] tabIds = {R.id.tabHome, R.id.tabModes, R.id.tabLanguages, R.id.tabVoices, R.id.tabAdvanced, R.id.tabMore};
        for (int id : tabIds) {
            android.widget.Button tab = findViewById(id);
            if (tab != null) {
                tab.setOnClickListener(v -> {
                    // सभी टैब्स को नार्मल (इनएक्टिव) रंग में सेट करें
                    for (int tid : tabIds) {
                        android.widget.Button t = findViewById(tid);
                        if (t != null) {
                            t.setBackgroundColor(android.graphics.Color.parseColor("#455A64"));
                            t.setTextColor(android.graphics.Color.parseColor("#B0BEC5"));
                        }
                    }
                    // क्लिक किए गए टैब को हरा (एक्टिव) करें
                    tab.setBackgroundColor(android.graphics.Color.parseColor("#00E676"));
                    tab.setTextColor(android.graphics.Color.parseColor("#FFFFFF"));
                    
                    android.widget.Toast.makeText(this, tab.getText() + " Tab Selected", android.widget.Toast.LENGTH_SHORT).show();
                    
                    // भविष्य में: यहाँ हम Views (Screens) स्विच करने का कोड डालेंगे
                });
            }
        }
    }


    private void setupSmartAnnouncerUI() {
        android.content.SharedPreferences prefs = getSharedPreferences("speechplus_prefs", MODE_PRIVATE);
        
        // Find Views
        android.widget.Switch swSilent = findViewById(R.id.switchSilentMode);
        android.widget.Switch swBattery = findViewById(R.id.switchBattery);
        android.widget.Spinner spBatteryInt = findViewById(R.id.spinnerBatteryInterval);
        android.widget.Switch swTime = findViewById(R.id.switchTime);
        android.widget.Spinner spTimeFmt = findViewById(R.id.spinnerTimeFormat);
        android.widget.Spinner spTimeInt = findViewById(R.id.spinnerTimeInterval);
        android.widget.Switch swCall = findViewById(R.id.switchCall);
        android.widget.Spinner spCallFmt = findViewById(R.id.spinnerCallFormat);
        android.widget.Spinner spCallRep = findViewById(R.id.spinnerCallRepeat);

        if (swSilent == null || swBattery == null) return; // UI not loaded yet

        // Load Saved States
        swSilent.setChecked(prefs.getBoolean("respect_silent_mode", false));
        swBattery.setChecked(prefs.getBoolean("enable_battery_announcer", false));
        spBatteryInt.setSelection(prefs.getInt("battery_interval_mode", 0));
        swTime.setChecked(prefs.getBoolean("enable_time_announcer", false));
        spTimeFmt.setSelection(prefs.getInt("time_format_mode", 0));
        spTimeInt.setSelection(prefs.getInt("time_interval_mode", 3));
        swCall.setChecked(prefs.getBoolean("enable_call_announcer", false));
        spCallFmt.setSelection(prefs.getInt("call_format_mode", 0));
        spCallRep.setSelection(prefs.getInt("call_repeat_count", 0));

        // Set Listeners to Save Data instantly
        swSilent.setOnCheckedChangeListener((btn, isChecked) -> prefs.edit().putBoolean("respect_silent_mode", isChecked).apply());
        swBattery.setOnCheckedChangeListener((btn, isChecked) -> prefs.edit().putBoolean("enable_battery_announcer", isChecked).apply());
        swTime.setOnCheckedChangeListener((btn, isChecked) -> prefs.edit().putBoolean("enable_time_announcer", isChecked).apply());
        swCall.setOnCheckedChangeListener((btn, isChecked) -> prefs.edit().putBoolean("enable_call_announcer", isChecked).apply());

        android.widget.AdapterView.OnItemSelectedListener spinnerListener = new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int pos, long id) {
                if (parent == spBatteryInt) prefs.edit().putInt("battery_interval_mode", pos).apply();
                else if (parent == spTimeFmt) prefs.edit().putInt("time_format_mode", pos).apply();
                else if (parent == spTimeInt) prefs.edit().putInt("time_interval_mode", pos).apply();
                else if (parent == spCallFmt) prefs.edit().putInt("call_format_mode", pos).apply();
                else if (parent == spCallRep) prefs.edit().putInt("call_repeat_count", pos).apply();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        };

        spBatteryInt.setOnItemSelectedListener(spinnerListener);
        spTimeFmt.setOnItemSelectedListener(spinnerListener);
        spTimeInt.setOnItemSelectedListener(spinnerListener);
        spCallFmt.setOnItemSelectedListener(spinnerListener);
        spCallRep.setOnItemSelectedListener(spinnerListener);
        
        // Start Service if any announcer is enabled
        startService(new Intent(this, SmartAnnouncerService.class));
    }


    private android.media.MediaPlayer audioMediaPlayer;
    private java.io.File generatedAudioFile;
    private android.speech.tts.TextToSpeech audioGenTts;

    private void setupAudioGeneratorHub() {
        android.widget.EditText editAudioText = findViewById(R.id.editAudioText);
        android.widget.Button btnGenerateAudio = findViewById(R.id.btnGenerateAudio);
        android.widget.LinearLayout layoutAudioStatus = findViewById(R.id.layoutAudioStatus);
        android.widget.TextView tvAudioStatus = findViewById(R.id.tvAudioStatus);
        android.widget.ProgressBar progressAudioGen = findViewById(R.id.progressAudioGen);
        android.widget.LinearLayout layoutMediaControls = findViewById(R.id.layoutMediaControls);
        android.widget.LinearLayout layoutExportControls = findViewById(R.id.layoutExportControls);
        android.widget.Button btnPlayPause = findViewById(R.id.btnPlayPauseAudio);
        android.widget.Button btnRewind = findViewById(R.id.btnRewind);
        android.widget.Button btnFastForward = findViewById(R.id.btnFastForward);
        android.widget.Button btnShare = findViewById(R.id.btnShareAudio);
        android.widget.Button btnSave = findViewById(R.id.btnSaveAudio);

        if (btnGenerateAudio == null) return;

        btnGenerateAudio.setEnabled(false);
        audioGenTts = new android.speech.tts.TextToSpeech(this, status -> {
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                btnGenerateAudio.setEnabled(true);
                audioGenTts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
    //                         runOnUiThread(() -> tvAudioStatus.setText("Processing text into audio..."));
                    }
                    @Override
                    public void onDone(String utteranceId) {
    //                         runOnUiThread(() -> {
                            layoutAudioStatus.setVisibility(android.view.View.GONE);
                            layoutMediaControls.setVisibility(android.view.View.VISIBLE);
                            layoutExportControls.setVisibility(android.view.View.VISIBLE);
                            btnGenerateAudio.setEnabled(true);
                            android.widget.Toast.makeText(MainActivity.this, "Audio Generated Successfully!", android.widget.Toast.LENGTH_SHORT).show();
                        });
                    }
                    @Override
                    public void onError(String utteranceId) {
    //                         runOnUiThread(() -> {
                            layoutAudioStatus.setVisibility(android.view.View.GONE);
                            btnGenerateAudio.setEnabled(true);
                            android.widget.Toast.makeText(MainActivity.this, "Error generating audio.", android.widget.Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            }
        });

        btnGenerateAudio.setOnClickListener(v -> {
            String text = editAudioText.getText().toString().trim();
            if (text.isEmpty()) {
                android.widget.Toast.makeText(this, "Please enter some text!", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            
            layoutMediaControls.setVisibility(android.view.View.GONE);
            layoutExportControls.setVisibility(android.view.View.GONE);
            layoutAudioStatus.setVisibility(android.view.View.VISIBLE);
            tvAudioStatus.setText("Initializing TTS Engine...");
            progressAudioGen.setIndeterminate(true);
            btnGenerateAudio.setEnabled(false);

            if (audioMediaPlayer != null) {
                audioMediaPlayer.release();
                audioMediaPlayer = null;
                btnPlayPause.setText("▶ Play");
            }

            generatedAudioFile = new java.io.File(getExternalCacheDir(), "ITFB_Generated_Audio.wav");
            java.util.HashMap<String, String> params = new java.util.HashMap<>();
            params.put(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "GENERATE_AUDIO");
            audioGenTts.synthesizeToFile(text, params, generatedAudioFile.getAbsolutePath());
        });

        btnPlayPause.setOnClickListener(v -> {
            if (generatedAudioFile == null || !generatedAudioFile.exists()) return;
            try {
                if (audioMediaPlayer == null) {
                    audioMediaPlayer = new android.media.MediaPlayer();
                    audioMediaPlayer.setDataSource(generatedAudioFile.getAbsolutePath());
                    audioMediaPlayer.prepare();
                    audioMediaPlayer.setOnCompletionListener(mp -> btnPlayPause.setText("▶ Play"));
                }
                if (audioMediaPlayer.isPlaying()) {
                    audioMediaPlayer.pause();
                    btnPlayPause.setText("▶ Play");
                } else {
                    audioMediaPlayer.start();
                    btnPlayPause.setText("⏸ Pause");
                }
            } catch (Exception e) { e.printStackTrace(); }
        });

        btnRewind.setOnClickListener(v -> {
            if (audioMediaPlayer != null && audioMediaPlayer.isPlaying()) {
                audioMediaPlayer.seekTo(Math.max(audioMediaPlayer.getCurrentPosition() - 10000, 0));
            }
        });

        btnFastForward.setOnClickListener(v -> {
            if (audioMediaPlayer != null && audioMediaPlayer.isPlaying()) {
                audioMediaPlayer.seekTo(Math.min(audioMediaPlayer.getCurrentPosition() + 10000, audioMediaPlayer.getDuration()));
            }
        });

        btnShare.setOnClickListener(v -> {
            if (generatedAudioFile == null || !generatedAudioFile.exists()) return;
            // Bypass strict mode safely for sharing file URIs
            android.os.StrictMode.VmPolicy.Builder builder = new android.os.StrictMode.VmPolicy.Builder();
            android.os.StrictMode.setVmPolicy(builder.build());
            
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            intent.setType("audio/wav");
            intent.putExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri.fromFile(generatedAudioFile));
            startActivity(android.content.Intent.createChooser(intent, "Share Audio via"));
        });

        btnSave.setOnClickListener(v -> {
            if (generatedAudioFile == null || !generatedAudioFile.exists()) return;
            try {
                java.io.File downloadsFolder = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                java.io.File destFile = new java.io.File(downloadsFolder, "ITFB_Audio_" + System.currentTimeMillis() + ".wav");
                
                java.io.FileInputStream in = new java.io.FileInputStream(generatedAudioFile);
                java.io.FileOutputStream out = new java.io.FileOutputStream(destFile);
                byte[] buffer = new byte[1024];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                in.close();
                out.flush();
                out.close();
                android.widget.Toast.makeText(this, "Saved to Downloads: " + destFile.getName(), android.widget.Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                android.widget.Toast.makeText(this, "Save Failed: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

}
