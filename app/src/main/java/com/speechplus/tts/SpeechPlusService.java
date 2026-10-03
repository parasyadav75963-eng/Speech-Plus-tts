package com.speechplus.tts;

import org.json.JSONObject;
import java.util.Locale;
import java.util.Set;
import android.speech.tts.Voice;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.SynthesisCallback;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeechService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class SpeechPlusService extends TextToSpeechService {

    private TextToSpeech primaryTts;
    private TextToSpeech secondaryTts;
    private SharedPreferences prefs;
    private boolean isPrimaryReady = false;
    private boolean isSecondaryReady = false;
    private String currentPrimaryEngine = "";
    private String currentSecondaryEngine = "";
    private int lastAudioRouting = -1;
    private static final Pattern REGIONAL_PATTERN = Pattern.compile("[\\p{IsDevanagari}]+");
    private final Map<String, TextToSpeech> extraEngines = new HashMap<>();
    private final Set<String> readyExtraEngines = new HashSet<>();

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("SpeechPlusPrefs", MODE_PRIVATE);
        initEngines();
    }

    private synchronized void initEngines() {
        if (prefs == null) prefs = getSharedPreferences("SpeechPlusPrefs", MODE_PRIVATE);
        String pEng = prefs.getString("primary_engine", "");
        String sEng = prefs.getString("secondary_engine", "");

        if (primaryTts == null || !pEng.equals(currentPrimaryEngine)) {
            currentPrimaryEngine = pEng;
            isPrimaryReady = false;
            if (primaryTts != null) { try { primaryTts.shutdown(); } catch (Exception ignored) {} }
            primaryTts = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS) { isPrimaryReady = true; applyAudioRouting(primaryTts, pEng); }
            }, pEng.isEmpty() ? null : pEng);
        }

        if (secondaryTts == null || !sEng.equals(currentSecondaryEngine)) {
            currentSecondaryEngine = sEng;
            isSecondaryReady = false;
            if (secondaryTts != null) { try { secondaryTts.shutdown(); } catch (Exception ignored) {} }
            if (!sEng.isEmpty()) {
                secondaryTts = new TextToSpeech(this, status -> {
                    if (status == TextToSpeech.SUCCESS) { isSecondaryReady = true; applyAudioRouting(secondaryTts, sEng); }
                }, sEng);
            } else { secondaryTts = null; }
        }
    }

    private boolean isLegacyEngine(String pkg) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase(Locale.US);
        return p.contains("eloquence") || p.contains("smartvoice") || p.contains("codefactory") || p.contains("vocalizer");
    }

    private void applyAudioRouting(TextToSpeech engine, String pkgName) {
        if (engine == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        try {
            if (isLegacyEngine(pkgName)) return; // DO NOT crash legacy engines with AudioAttributes
            int routing = prefs.getInt("audio_routing", 0);
            AudioAttributes.Builder attrs = new AudioAttributes.Builder();
            if (routing == 1) {
                attrs.setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH);
            } else {
                attrs.setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH);
            }
            engine.setAudioAttributes(attrs.build());
        } catch (Throwable ignored) {}
    }

    private boolean containsRegional(String text) {
        return text != null && REGIONAL_PATTERN.matcher(text).find();
    }

    // 100% TalkBack Crash Fix
    @Override
    protected int onIsLanguageAvailable(String lang, String country, String variant) { return TextToSpeech.LANG_AVAILABLE; }
    @Override
    protected String[] onGetLanguage() { return new String[]{"hin", "IND", ""}; }
    @Override
    protected int onLoadLanguage(String lang, String country, String variant) { return TextToSpeech.LANG_AVAILABLE; }

    @Override
    protected void onStop() {
        if (primaryTts != null) { try { primaryTts.stop(); } catch (Exception ignored) {} }
        if (secondaryTts != null) { try { secondaryTts.stop(); } catch (Exception ignored) {} }
    }

    @Override
    protected synchronized void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
        String text = request.getCharSequenceText() != null ? request.getCharSequenceText().toString() : request.getText();
        if (text == null || text.trim().isEmpty()) {
            callback.start(16000, AudioFormat.ENCODING_PCM_16BIT, 1);
            callback.done();
            return;
        }
        text = text.trim();
        initEngines();

        int routing = prefs.getInt("audio_routing", 0);
        if (routing != lastAudioRouting) {
            lastAudioRouting = routing;
            applyAudioRouting(primaryTts, currentPrimaryEngine);
            applyAudioRouting(secondaryTts, currentSecondaryEngine);
        }

        boolean forceRate = prefs.getBoolean("force_rate", true);
        boolean forcePitch = prefs.getBoolean("force_pitch", true);
        float speechRate = forceRate ? (prefs.getInt("voice_tab_rate_progress", 20) / 20.0f) : (request.getSpeechRate() / 100.0f);
        float pitch = forcePitch ? (prefs.getInt("voice_tab_pitch_progress", 20) / 20.0f) : (request.getPitch() / 100.0f);
        if (speechRate <= 0.1f) speechRate = 1.0f;
        if (pitch <= 0.1f) pitch = 1.0f;

        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, prefs.getInt("voice_tab_volume", 100) / 100.0f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, routing == 1 ? AudioManager.STREAM_ACCESSIBILITY : AudioManager.STREAM_MUSIC);
        }

        int mode = prefs.getInt("tts_mode", 0);
        onStop(); // Zero Lag Cutoff

        if (mode == 0) {
            speakOnEngine(primaryTts, isPrimaryReady, text, speechRate, pitch, params, true, "");
        } else {
            synthesizeSmartDual(text, speechRate, pitch, params);
        }

        callback.start(16000, AudioFormat.ENCODING_PCM_16BIT, 1);
        callback.done();
    }

    private void synthesizeSmartDual(String text, float baseRate, float basePitch, Bundle baseParams) {
        Pattern pattern = Pattern.compile("([\\p{IsDevanagari}]+)|([^\\p{IsDevanagari}]+)");
        Matcher matcher = pattern.matcher(text);
        boolean isFirst = true;

        while (matcher.find()) {
            String chunk = matcher.group().trim();
            if (chunk.isEmpty()) continue;

            boolean isHindi = containsRegional(chunk);
            String langCode = isHindi ? "hi" : "en";
            
            // 🔥 RESTORED: This links to your "Voices" tab settings (e.g. Hindi -> Vocalizer) 🔥
            JSONObject langConfig = findLanguageConfig(langCode, "", chunk);
            if (langConfig != null) {
                String engPkg = langConfig.optString("engine_pkg", "");
                if (!"disabled".equals(engPkg)) {
                    float langRate = (float) langConfig.optDouble("rate", baseRate);
                    float langPitch = (float) langConfig.optDouble("pitch", basePitch);
                    Bundle chunkParams = new Bundle(baseParams);
                    chunkParams.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, langConfig.optInt("volume", 100) / 100.0f);

                    TextToSpeech targetTts = getOrCreateEngine(engPkg);
                    if (targetTts != null && isEngineReady(engPkg, targetTts)) {
                        applyVoiceAndLocale(targetTts, langCode, langConfig.optString("voice_variant", "Default"));
                        speakOnEngine(targetTts, true, chunk, langRate, langPitch, chunkParams, isFirst, langCode);
                        isFirst = false;
                        continue;
                    }
                }
            }

            // Fallback if Voices tab is not set
            TextToSpeech target = (isHindi && isSecondaryReady && secondaryTts != null) ? secondaryTts : primaryTts;
            boolean ready = (isHindi && isSecondaryReady && secondaryTts != null) ? isSecondaryReady : isPrimaryReady;
            speakOnEngine(target, ready, chunk, baseRate, basePitch, baseParams, isFirst, langCode);
            isFirst = false;
        }
    }

    private void speakOnEngine(TextToSpeech tts, boolean isReady, String text, float rate, float pitch, Bundle params, boolean flush, String langCode) {
        if (tts != null && isReady) {
            try {
                if (langCode != null && !langCode.isEmpty()) { tts.setLanguage(Locale.forLanguageTag(langCode)); }
                tts.setSpeechRate(rate); tts.setPitch(pitch);
                tts.speak(text, flush ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, params, "SP_" + System.currentTimeMillis());
            } catch (Exception ignored) {}
        }
    }

    private synchronized TextToSpeech getOrCreateEngine(String pkg) {
        if (pkg == null || pkg.isEmpty() || "disabled".equals(pkg)) return null;
        if (pkg.equals(currentPrimaryEngine)) return primaryTts;
        if (pkg.equals(currentSecondaryEngine)) return secondaryTts;
        if (extraEngines.containsKey(pkg)) return extraEngines.get(pkg);
        TextToSpeech engine = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) synchronized (readyExtraEngines) { readyExtraEngines.add(pkg); }
        }, pkg);
        extraEngines.put(pkg, engine);
        return engine;
    }

    private synchronized boolean isEngineReady(String pkg, TextToSpeech engine) {
        if (engine == null) return false;
        if (engine == primaryTts) return isPrimaryReady;
        if (engine == secondaryTts) return isSecondaryReady;
        synchronized (readyExtraEngines) { return readyExtraEngines.contains(pkg); }
    }

    private JSONObject findLanguageConfig(String reqLang, String reqCountry, String text) {
        if (prefs == null) return null;
        Set<String> configuredKeys = prefs.getStringSet("configured_lang_keys", null);
        if (configuredKeys == null || configuredKeys.isEmpty()) return null;
        String langPart = (reqLang != null) ? reqLang.toLowerCase().trim() : "";
        for (String key : configuredKeys) {
            String code = key.replace("lang_map_", "").toLowerCase().trim();
            if (code.equals(langPart) || code.startsWith(langPart)) {
                try { return new JSONObject(prefs.getString(key, "")); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private void applyVoiceAndLocale(TextToSpeech tts, String langCode, String variant) {
        if (tts == null) return;
        try { if (langCode != null && !langCode.isEmpty()) tts.setLanguage(Locale.forLanguageTag(langCode)); } catch (Exception ignored) {}
        if (variant != null && !variant.equals("Default") && !variant.equals("Default Voice")) {
            try { String engName = tts.getDefaultEngine(); if (engName != null && engName.toLowerCase(Locale.US).contains("eloquence")) return; } catch (Throwable ignored) {}
            try {
                Set<Voice> voices = tts.getVoices();
                if (voices != null) { for (Voice v : voices) { if (v.getName().equals(variant)) { tts.setVoice(v); break; } } }
            } catch (Exception ignored) {}
        }
    }
}
