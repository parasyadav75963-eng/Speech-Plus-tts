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
    private static final Pattern REGIONAL_PATTERN = Pattern.compile("[\\p{IsDevanagari}\\p{IsBengali}\\p{IsTamil}\\p{IsTelugu}\\p{IsGujarati}\\p{IsMalayalam}\\p{IsKannada}\\p{IsOriya}\\p{IsGurmukhi}]+");
    private final Map<String, TextToSpeech> extraEngines = new HashMap<>();

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
            currentPrimaryEngine = pEng; isPrimaryReady = false;
            if (primaryTts != null) { try { primaryTts.shutdown(); } catch (Exception ignored) {} }
            if (!pEng.isEmpty()) primaryTts = new TextToSpeech(this, status -> { if (status == TextToSpeech.SUCCESS) isPrimaryReady = true; }, pEng);
        }

        if (secondaryTts == null || !sEng.equals(currentSecondaryEngine)) {
            currentSecondaryEngine = sEng; isSecondaryReady = false;
            if (secondaryTts != null) { try { secondaryTts.shutdown(); } catch (Exception ignored) {} }
            if (!sEng.isEmpty()) secondaryTts = new TextToSpeech(this, status -> { if (status == TextToSpeech.SUCCESS) isSecondaryReady = true; }, sEng);
            else secondaryTts = null;
        }
    }

    private boolean isLegacyEngine(String pkg) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase(Locale.US);
        return p.contains("eloquence") || p.contains("smartvoice") || p.contains("vocalizer");
    }

    private boolean containsRegional(String text) { return text != null && REGIONAL_PATTERN.matcher(text).find(); }
    
    @Override protected int onIsLanguageAvailable(String lang, String country, String variant) { return TextToSpeech.LANG_AVAILABLE; }
    @Override protected String[] onGetLanguage() { return new String[]{"hin", "IND", ""}; }
    @Override protected int onLoadLanguage(String lang, String country, String variant) { return TextToSpeech.LANG_AVAILABLE; }

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
            callback.done(); return;
        }
        text = text.trim(); initEngines();

        float speechRate = prefs.getBoolean("force_rate", true) ? (prefs.getInt("voice_tab_rate_progress", 20) / 20.0f) : (request.getSpeechRate() / 100.0f);
        float pitch = prefs.getBoolean("force_pitch", true) ? (prefs.getInt("voice_tab_pitch_progress", 20) / 20.0f) : (request.getPitch() / 100.0f);
        if (speechRate <= 0.1f) speechRate = 1.0f; if (pitch <= 0.1f) pitch = 1.0f;

        Bundle params = new Bundle();
        
        float volume = prefs.getInt("voice_tab_volume", 100) / 100.0f;
        if (prefs.getBoolean("amplify_volume", true)) {
            volume = Math.min(1.0f, volume * 1.5f);
        }
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, prefs.getInt("audio_routing", 0) == 1 ? AudioManager.STREAM_ACCESSIBILITY : AudioManager.STREAM_MUSIC);
        }

        onStop();

        if (prefs.getInt("tts_mode", 0) == 0) {
            speakOnEngine(primaryTts, isPrimaryReady, text, speechRate, pitch, params, true, "", currentPrimaryEngine);
        } else {
            synthesizeSmartDual(text, speechRate, pitch, params);
        }

        callback.start(16000, AudioFormat.ENCODING_PCM_16BIT, 1); callback.done();
    }

    private void synthesizeSmartDual(String text, float baseRate, float basePitch, Bundle baseParams) {
        Matcher matcher = Pattern.compile("([\\p{IsDevanagari}\\p{IsBengali}\\p{IsTamil}\\p{IsTelugu}\\p{IsGujarati}\\p{IsMalayalam}\\p{IsKannada}\\p{IsOriya}\\p{IsGurmukhi}]+)|([^\\p{IsDevanagari}\\p{IsBengali}\\p{IsTamil}\\p{IsTelugu}\\p{IsGujarati}\\p{IsMalayalam}\\p{IsKannada}\\p{IsOriya}\\p{IsGurmukhi}]+)").matcher(text);
        boolean isFirst = true;

        while (matcher.find()) {
            String chunk = matcher.group().trim();
            if (chunk.isEmpty()) continue;
            boolean isRegional = containsRegional(chunk);
            TextToSpeech target = (isRegional && isSecondaryReady && secondaryTts != null) ? secondaryTts : primaryTts;
            boolean ready = (isRegional && isSecondaryReady && secondaryTts != null) ? isSecondaryReady : isPrimaryReady;
            String engPkg = (isRegional && isSecondaryReady && secondaryTts != null) ? currentSecondaryEngine : currentPrimaryEngine;
            speakOnEngine(target, ready, chunk, baseRate, basePitch, baseParams, isFirst, isRegional ? "hi" : "en", engPkg);
            isFirst = false;
        }
    }

    private void speakOnEngine(TextToSpeech tts, boolean isReady, String text, float rate, float pitch, Bundle params, boolean flush, String langCode, String enginePkg) {
        if (tts != null && isReady) {
            try {
                if (langCode != null && !langCode.isEmpty()) { try { tts.setLanguage(Locale.forLanguageTag(langCode)); } catch (Throwable ignored) {} }
                tts.setSpeechRate(rate); tts.setPitch(pitch);
                Bundle safeParams = params != null ? new Bundle(params) : new Bundle();
                
                if (isLegacyEngine(enginePkg) || prefs.getBoolean("strip_attributes", false)) safeParams.remove(TextToSpeech.Engine.KEY_PARAM_STREAM);

                boolean isQuickChar = prefs.getBoolean("quick_char", false) && text != null && text.length() <= 2;
                int queueMode = (flush || isQuickChar) ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;

                tts.speak(text, queueMode, safeParams, "SP_" + System.currentTimeMillis());
            } catch (Throwable ignored) {}
        }
    }
}
