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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.android.gms.tasks.Tasks;

public class SpeechPlusService extends TextToSpeechService {
    private TextToSpeech primaryTts;
    private TextToSpeech secondaryTts;
    private SharedPreferences prefs;
    private boolean isPrimaryReady = false;
    private boolean isSecondaryReady = false;
    private String currentPrimaryEngine = "";
    private String currentSecondaryEngine = "";
    private final Map<String, TextToSpeech> extraEngines = new HashMap<>();
    
    private volatile boolean isStopped = false;
    private volatile CountDownLatch syncLatch = null; 

    @Override public void onCreate() { super.onCreate(); prefs = getSharedPreferences("SpeechPlusPrefs", MODE_PRIVATE); initEngines(); }
    
    private void enforceAccessibilityStream(TextToSpeech tts) {
        if (tts == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
            try { tts.setAudioAttributes(attrs); } catch (Exception ignored) {}
        }
    }

    private synchronized void initEngines() {
        if (prefs == null) prefs = getSharedPreferences("SpeechPlusPrefs", MODE_PRIVATE);
        String pEng = prefs.getString("primary_engine", ""); String sEng = prefs.getString("secondary_engine", "");
        
        if (primaryTts == null || !pEng.equals(currentPrimaryEngine)) {
            currentPrimaryEngine = pEng; isPrimaryReady = false;
            if (primaryTts != null) { try { primaryTts.shutdown(); } catch (Exception ignored) {} }
            if (!pEng.isEmpty()) primaryTts = new TextToSpeech(this, status -> { 
                if (status == TextToSpeech.SUCCESS) { isPrimaryReady = true; enforceAccessibilityStream(primaryTts); } 
            }, pEng);
        }
        if (secondaryTts == null || !sEng.equals(currentSecondaryEngine)) {
            currentSecondaryEngine = sEng; isSecondaryReady = false;
            if (secondaryTts != null) { try { secondaryTts.shutdown(); } catch (Exception ignored) {} }
            if (!sEng.isEmpty()) secondaryTts = new TextToSpeech(this, status -> { 
                if (status == TextToSpeech.SUCCESS) { isSecondaryReady = true; enforceAccessibilityStream(secondaryTts); } 
            }, sEng);
            else secondaryTts = null;
        }
        
        Set<String> keys = prefs.getStringSet("configured_lang_keys", new HashSet<>());
        for (String k : keys) { try { JSONObject obj = new JSONObject(prefs.getString(k, "")); String ePkg = obj.optString("engine_pkg", ""); if (!ePkg.isEmpty() && !"disabled".equals(ePkg)) getOrCreateEngine(ePkg); } catch(Exception ignored){} }
    }

    @Override protected int onIsLanguageAvailable(String lang, String country, String variant) { return TextToSpeech.LANG_AVAILABLE; }
    @Override protected String[] onGetLanguage() { return new String[]{"hin", "IND", ""}; }
    @Override protected int onLoadLanguage(String lang, String country, String variant) { return TextToSpeech.LANG_AVAILABLE; }
    
    @Override protected void onStop() {
        isStopped = true;
        if (primaryTts != null) { try { primaryTts.stop(); } catch (Exception ignored) {} }
        if (secondaryTts != null) { try { secondaryTts.stop(); } catch (Exception ignored) {} }
        for (TextToSpeech tts : extraEngines.values()) { if (tts != null) { try { tts.stop(); } catch (Exception ignored) {} } }
        if (syncLatch != null) { syncLatch.countDown(); }
    }

    @Override protected synchronized void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
        isStopped = false;
        String text = request.getCharSequenceText() != null ? request.getCharSequenceText().toString() : request.getText();
        if (text == null || text.trim().isEmpty()) { callback.start(16000, AudioFormat.ENCODING_PCM_16BIT, 1); callback.done(); return; }
        text = text.trim(); initEngines();

        // 🔥 TRUE ENGINE DEFAULT FIX: 
        // अगर Force Checkbox टिक नहीं है, तो हम ऐप के प्रोग्रेस को बिल्कुल बाईपास करके TalkBack/System की ओरिजिनल वैल्यू भेजेंगे।
        boolean forceRate = prefs.getBoolean("force_rate", false);
        boolean forcePitch = prefs.getBoolean("force_pitch", false);

        float speechRate;
        float pitch;

        if (forceRate) {
            float appRate = prefs.getInt("voice_tab_rate_progress", 50) / 50.0f;
            speechRate = Math.max(0.1f, Math.min(appRate, 3.0f));
        } else {
            // TalkBack या System की डिफ़ॉल्ट ओरिजिनल स्पीड का उपयोग करें (कोई छेड़छाड़ नहीं)
            speechRate = request.getSpeechRate() > 0 ? request.getSpeechRate() / 100.0f : 1.0f;
        }

        if (forcePitch) {
            float appPitch = prefs.getInt("voice_tab_pitch_progress", 50) / 50.0f;
            pitch = Math.max(0.1f, Math.min(appPitch, 3.0f));
        } else {
            // TalkBack या System की डिफ़ॉल्ट ओरिजिनल पिच का उपयोग करें (आवाज़ मोटी/पतली नहीं होगी)
            pitch = request.getPitch() > 0 ? request.getPitch() / 100.0f : 1.0f;
        }

        Bundle params = new Bundle();
        callback.start(16000, AudioFormat.ENCODING_PCM_16BIT, 1);

        int mode = prefs.getInt("tts_mode", 0);
        
        if (mode == 0) {
            speakAndWait(primaryTts, isPrimaryReady, text, speechRate, pitch, params);
        } else {
            if (text.length() <= 3) {
                String quickLang = text.matches(".*[\\u0900-\\u097F].*") ? "hi" : "en";
                routeAndSpeak(text, speechRate, pitch, params, quickLang);
            } else {
                synthesizeAutoTTS(text, speechRate, pitch, params);
            }
        }
        
        callback.done(); 
    }

    private void synthesizeAutoTTS(String text, float baseRate, float basePitch, Bundle baseParams) {
        String[] chunks = text.split("(?<=[\\u0900-\\u097F])\\s*(?=[a-zA-Z])|(?<=[a-zA-Z])\\s*(?=[\\u0900-\\u097F])|(?<=[.!?\\n])\\s+"); 
        LanguageIdentifier langId = LanguageIdentification.getClient();
        
        for (String chunk : chunks) {
            if (isStopped) break; 
            chunk = chunk.trim(); if (chunk.isEmpty()) continue;
            
            String langCode = "en";
            if(chunk.matches(".*[\\u0900-\\u097F].*")) {
                langCode = "hi"; 
            } else if (chunk.length() > 3) {
                try { String dl = Tasks.await(langId.identifyLanguage(chunk)); if (!"und".equals(dl)) langCode = dl; } catch (Exception ignored) {}
            }
            routeAndSpeak(chunk, baseRate, basePitch, baseParams, langCode);
        }
    }

    private void routeAndSpeak(String chunk, float baseRate, float basePitch, Bundle baseParams, String langCode) {
        if (isStopped) return;
        JSONObject langConfig = findLanguageConfig(langCode, chunk);
        if (langConfig != null) {
            String engPkg = langConfig.optString("engine_pkg", "");
            if (!"disabled".equals(engPkg)) {
                float lRate = prefs.getBoolean("force_rate", false) ? (float) langConfig.optDouble("rate", baseRate) : baseRate;
                float lPitch = prefs.getBoolean("force_pitch", false) ? (float) langConfig.optDouble("pitch", basePitch) : basePitch;
                
                TextToSpeech targetTts = getOrCreateEngine(engPkg);
                if (targetTts != null) {
                    applyVoiceAndLocale(targetTts, langCode, langConfig.optString("voice_variant", "Default"));
                    speakAndWait(targetTts, true, chunk, lRate, lPitch, baseParams);
                    return;
                }
            }
        }
        speakAndWait(primaryTts, isPrimaryReady, chunk, baseRate, basePitch, baseParams);
    }

    private void speakAndWait(TextToSpeech tts, boolean isReady, String text, float rate, float pitch, Bundle params) {
        if (tts == null || !isReady || isStopped) return;
        try {
            tts.setSpeechRate(rate); 
            tts.setPitch(pitch);
            enforceAccessibilityStream(tts);
            
            Bundle safeParams = new Bundle();
            safeParams.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ACCESSIBILITY);
            safeParams.putString(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_ACCESSIBILITY));
            
            String utteranceId = "SP_" + System.currentTimeMillis();
            syncLatch = new CountDownLatch(1);
            
            tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                @Override public void onStart(String s) {}
                @Override public void onDone(String s) { if(s.equals(utteranceId) && syncLatch != null) syncLatch.countDown(); }
                @Override public void onError(String s) { if(s.equals(utteranceId) && syncLatch != null) syncLatch.countDown(); }
            });
            
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, safeParams, utteranceId);
            
            try { syncLatch.await(8, TimeUnit.SECONDS); } catch (Exception ignored) {}
            syncLatch = null;

        } catch (Throwable ignored) {}
    }

    private synchronized TextToSpeech getOrCreateEngine(String pkg) {
        if (pkg == null || pkg.isEmpty() || "disabled".equals(pkg)) return null;
        if (pkg.equals(currentPrimaryEngine)) return primaryTts;
        if (pkg.equals(currentSecondaryEngine)) return secondaryTts;
        if (extraEngines.containsKey(pkg)) return extraEngines.get(pkg);
        TextToSpeech engine = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) { enforceAccessibilityStream(extraEngines.get(pkg)); }
        }, pkg); 
        extraEngines.put(pkg, engine); return engine;
    }
    
    private JSONObject findLanguageConfig(String reqLang, String text) {
        if (prefs == null) return null; Set<String> keys = prefs.getStringSet("configured_lang_keys", null);
        if (keys == null || keys.isEmpty()) return null;
        String lp = (reqLang != null) ? reqLang.toLowerCase().trim() : "";
        for (String k : keys) { String code = k.replace("lang_map_", "").toLowerCase().trim(); if (code.equals(lp) || code.startsWith(lp)) { try { return new JSONObject(prefs.getString(k, "")); } catch (Exception ignored) {} } }
        return null;
    }
    
    private void applyVoiceAndLocale(TextToSpeech tts, String langCode, String variant) {
        if (tts == null) return;
        try { if (langCode != null && !langCode.isEmpty()) tts.setLanguage(Locale.forLanguageTag(langCode)); } catch (Exception ignored) {}
        if (variant != null && !variant.equals("Default") && !variant.equals("Default Voice")) {
            try { Set<Voice> voices = tts.getVoices(); if (voices != null) { for (Voice v : voices) { if (v.getName().equals(variant)) { tts.setVoice(v); break; } } } } catch (Exception ignored) {}
        }
    }
}
