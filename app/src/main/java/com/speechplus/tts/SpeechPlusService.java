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

    @Override public void onCreate() { 
        super.onCreate(); 
        prefs = getSharedPreferences("SpeechPlusPrefs", MODE_PRIVATE); 
        initEngines(); 
    }
    
    private void enforceAccessibilityStream(TextToSpeech tts) {
        if (tts == null) return;
        boolean stripAttr = prefs != null && prefs.getBoolean("strip_attributes", false);
        if (!stripAttr && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
            try { tts.setAudioAttributes(attrs); } catch (Exception ignored) {}
        }
    }

    private synchronized void initEngines() {
        if (prefs == null) prefs = getSharedPreferences("SpeechPlusPrefs", MODE_PRIVATE);
        String pEng = prefs.getString("primary_engine", ""); 
        String sEng = prefs.getString("secondary_engine", "");
        
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
        for (String k : keys) { 
            try { 
                JSONObject obj = new JSONObject(prefs.getString(k, "")); 
                String ePkg = obj.optString("engine_pkg", ""); 
                if (!ePkg.isEmpty() && !"disabled".equals(ePkg)) getOrCreateEngine(ePkg); 
            } catch(Exception ignored){} 
        }
    }

    @Override protected int onIsLanguageAvailable(String lang, String country, String variant) { return TextToSpeech.LANG_AVAILABLE; }
    @Override protected String[] onGetLanguage() { return new String[]{"eng", "IND", ""}; }
    @Override protected int onLoadLanguage(String lang, String country, String variant) { return TextToSpeech.LANG_AVAILABLE; }
    
    @Override protected void onStop() {
        isStopped = true;
        if (syncLatch != null) { syncLatch.countDown(); }
        if (primaryTts != null) { try { primaryTts.stop(); } catch (Exception ignored) {} }
        if (secondaryTts != null) { try { secondaryTts.stop(); } catch (Exception ignored) {} }
        for (TextToSpeech tts : extraEngines.values()) { if (tts != null) { try { tts.stop(); } catch (Exception ignored) {} } }
    }

    @Override protected void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
        isStopped = false;
        CharSequence cs = request.getCharSequenceText();
        String rawText = cs != null ? cs.toString() : request.getText();
        
        callback.start(16000, AudioFormat.ENCODING_PCM_16BIT, 1);
        if (rawText == null || rawText.trim().isEmpty()) { 
            callback.done(); 
            return; 
        }
        String text = rawText.trim(); 
        initEngines();

        boolean forceRate = prefs.getBoolean("force_rate", false);
        boolean forcePitch = prefs.getBoolean("force_pitch", false);

        float speechRate = forceRate ? Math.max(0.1f, Math.min(prefs.getInt("voice_tab_rate_progress", 50) / 50.0f, 3.0f)) : (request.getSpeechRate() > 0 ? request.getSpeechRate() / 100.0f : 1.0f);
        float pitch = forcePitch ? Math.max(0.1f, Math.min(prefs.getInt("voice_tab_pitch_progress", 50) / 50.0f, 3.0f)) : (request.getPitch() > 0 ? request.getPitch() / 100.0f : 1.0f);

        Bundle params = new Bundle();
        int mode = prefs.getInt("tts_mode", 0);
        
        if (mode == 0) {
            speakAndWait(primaryTts, isPrimaryReady, text, speechRate, pitch, params, true);
        } else {
            // 🔥 SAMSUNG & KEYBOARD SHORT TEXT FIX: 
            // 12 अक्षर या उससे छोटे शब्दों (जैसे 'a', 'e', 'Store', 'Suno') को बिना किसी देरी के सीधे पढ़ा जाएगा
            if (text.length() <= 12 && !text.contains("\n")) {
                String quickLang = text.matches(".*[\\u0900-\\u097F].*") ? "hi" : "en";
                routeAndSpeak(text, speechRate, pitch, params, quickLang, true);
            } else {
                synthesizeAutoTTS(text, speechRate, pitch, params);
            }
        }
        
        // थोड़ा सा बफर डेटा भेजें ताकि TalkBack ऑडियो स्ट्रीम को एक्टिव रखे
        byte[] silence = new byte[320];
        callback.audioAvailable(silence, 0, silence.length);
        callback.done(); 
    }

    private void synthesizeAutoTTS(String text, float baseRate, float basePitch, Bundle baseParams) {
        String[] chunks = text.split("(?<=[.!?\\n])\\s+|(?<=[\\u0900-\\u097F])\\s*(?=[a-zA-Z])|(?<=[a-zA-Z])\\s*(?=[\\u0900-\\u097F])"); 
        LanguageIdentifier langId = LanguageIdentification.getClient();
        boolean isFirstChunk = true;
        
        for (String chunk : chunks) {
            if (isStopped) break; 
            chunk = chunk.trim(); 
            if (chunk.isEmpty()) continue;
            
            String langCode = "en";
            if(chunk.matches(".*[\\u0900-\\u097F].*")) {
                langCode = "hi"; 
            } else if (chunk.length() > 3) {
                try { 
                    String dl = Tasks.await(langId.identifyLanguage(chunk), 150, TimeUnit.MILLISECONDS); 
                    if (dl != null && !"und".equals(dl)) langCode = dl; 
                } catch (Exception ignored) {}
            }
            routeAndSpeak(chunk, baseRate, basePitch, baseParams, langCode, isFirstChunk);
            isFirstChunk = false;
        }
    }

    private void routeAndSpeak(String chunk, float baseRate, float basePitch, Bundle baseParams, String langCode, boolean isFirstChunk) {
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
                    speakAndWait(targetTts, true, chunk, lRate, lPitch, baseParams, isFirstChunk);
                    return;
                }
            }
        }
        speakAndWait(primaryTts, isPrimaryReady, chunk, baseRate, basePitch, baseParams, isFirstChunk);
    }

    private void speakAndWait(TextToSpeech tts, boolean isReady, String text, float rate, float pitch, Bundle params, boolean isFirstChunk) {
        if (tts == null || !isReady || isStopped) return;
        try {
            tts.setSpeechRate(rate); 
            tts.setPitch(pitch);
            enforceAccessibilityStream(tts);
            
            Bundle safeParams = new Bundle();
            safeParams.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ACCESSIBILITY);
            safeParams.putString(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_ACCESSIBILITY));
            
            // 🔥 SAMSUNG CUTOFF PADDING TRICK:
            // अगर टेक्स्ट बहुत छोटा है (जैसे 'a', 'e', 'Store'), तो आगे थोड़ा स्पेस जोड़ें ताकि ऑडियो कटे नहीं
            String speakText = text;
            if (text.length() <= 6) {
                speakText = " " + text + " ";
            }
            
            String utteranceId = "SP_" + System.nanoTime();
            CountDownLatch localLatch = new CountDownLatch(1);
            syncLatch = localLatch;
            
            tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                @Override public void onStart(String s) {}
                @Override public void onDone(String s) { if(s.equals(utteranceId)) localLatch.countDown(); }
                @Override public void onError(String s) { if(s.equals(utteranceId)) localLatch.countDown(); }
            });
            
            // पहला टुकड़ा हमेशा QUEUE_FLUSH होगा ताकि आवाज़ तुरंत निकले, और बाकी टुकड़े QUEUE_ADD होंगे
            int queueMode = isFirstChunk ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
            tts.speak(speakText, queueMode, safeParams, utteranceId);
            
            try { localLatch.await(8, TimeUnit.SECONDS); } catch (Exception ignored) {}

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
        for (String k : keys) { 
            String code = k.replace("lang_map_", "").toLowerCase().trim(); 
            if (code.equals(lp) || code.startsWith(lp)) { 
                try { return new JSONObject(prefs.getString(k, "")); } catch (Exception ignored) {} 
            } 
        }
        return null;
    }
    
    private void applyVoiceAndLocale(TextToSpeech tts, String langCode, String variant) {
        if (tts == null) return;
        try { if (langCode != null && !langCode.isEmpty()) tts.setLanguage(Locale.forLanguageTag(langCode)); } catch (Exception ignored) {}
        if (variant != null && !variant.equals("Default") && !variant.equals("Default Voice")) {
            try { 
                Set<Voice> voices = tts.getVoices(); 
                if (voices != null) { 
                    for (Voice v : voices) { 
                        if (v.getName().equals(variant)) { tts.setVoice(v); break; } 
                    } 
                } 
            } catch (Exception ignored) {}
        }
    }
}
