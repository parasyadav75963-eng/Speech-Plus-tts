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
    
    private volatile boolean isCancelled = false;
    private volatile CountDownLatch currentLatch = null; // 1000% Keyboard Spelling Lock

    @Override public void onCreate() { super.onCreate(); prefs = getSharedPreferences("SpeechPlusPrefs", MODE_PRIVATE); initEngines(); }
    
    private synchronized void initEngines() {
        if (prefs == null) prefs = getSharedPreferences("SpeechPlusPrefs", MODE_PRIVATE);
        String pEng = prefs.getString("primary_engine", ""); String sEng = prefs.getString("secondary_engine", "");
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
        Set<String> keys = prefs.getStringSet("configured_lang_keys", new HashSet<>());
        for (String k : keys) { try { JSONObject obj = new JSONObject(prefs.getString(k, "")); String ePkg = obj.optString("engine_pkg", ""); if (!ePkg.isEmpty() && !"disabled".equals(ePkg)) getOrCreateEngine(ePkg); } catch(Exception ignored){} }
    }

    private boolean isLegacyEngine(String pkg) { return pkg != null && (pkg.toLowerCase(Locale.US).contains("eloquence") || pkg.toLowerCase(Locale.US).contains("smartvoice") || pkg.toLowerCase(Locale.US).contains("vocalizer")); }
    @Override protected int onIsLanguageAvailable(String lang, String country, String variant) { return TextToSpeech.LANG_AVAILABLE; }
    @Override protected String[] onGetLanguage() { return new String[]{"hin", "IND", ""}; }
    @Override protected int onLoadLanguage(String lang, String country, String variant) { return TextToSpeech.LANG_AVAILABLE; }
    
    private void internalStop() {
        if (primaryTts != null) { try { primaryTts.stop(); } catch (Exception ignored) {} }
        if (secondaryTts != null) { try { secondaryTts.stop(); } catch (Exception ignored) {} }
        for (TextToSpeech tts : extraEngines.values()) { if (tts != null) { try { tts.stop(); } catch (Exception ignored) {} } }
    }

    @Override protected void onStop() {
        isCancelled = true;
        if (currentLatch != null) { currentLatch.countDown(); } // स्वाइप करते ही लॉक खोल देगा
        internalStop(); 
    }

    @Override protected synchronized void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
        isCancelled = false;
        String text = request.getCharSequenceText() != null ? request.getCharSequenceText().toString() : request.getText();
        if (text == null || text.trim().isEmpty()) { callback.start(16000, AudioFormat.ENCODING_PCM_16BIT, 1); callback.done(); return; }
        text = text.trim(); initEngines();

        // 🔥 PERFECT VOICE FIX (आवाज़ मोटी या पतली नहीं होगी)
        boolean forceRate = prefs.getBoolean("force_rate", false);
        boolean forcePitch = prefs.getBoolean("force_pitch", false);
        
        // 50 का मतलब 1.0f (एकदम नॉर्मल डिफ़ॉल्ट आवाज़) है
        float appRate = prefs.getInt("voice_tab_rate_progress", 50) / 50.0f;
        float appPitch = prefs.getInt("voice_tab_pitch_progress", 50) / 50.0f;
        
        float tbRate = request.getSpeechRate() > 0 ? request.getSpeechRate() / 100.0f : 1.0f;
        float tbPitch = request.getPitch() > 0 ? request.getPitch() / 100.0f : 1.0f;

        float speechRate = forceRate ? appRate : tbRate;
        float pitch = forcePitch ? appPitch : tbPitch;

        // लिमिट करें ताकि कभी फटे नहीं
        speechRate = Math.max(0.1f, Math.min(speechRate, 3.0f));
        pitch = Math.max(0.1f, Math.min(pitch, 3.0f));

        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, prefs.getInt("voice_tab_volume", 100) / 100.0f);

        int mode = prefs.getInt("tts_mode", 0);
        
        if (mode == 0) {
            speakOnEngineAndWait(primaryTts, isPrimaryReady, text, speechRate, pitch, params, "", currentPrimaryEngine);
        } else {
            synthesizeAutoTTS(text, speechRate, pitch, params);
        }
        
        callback.start(16000, AudioFormat.ENCODING_PCM_16BIT, 1); 
        callback.done(); // इंजन का काम 100% पूरा होने के बाद ही यह TalkBack को सिग्नल देगा
    }

    private void synthesizeAutoTTS(String text, float baseRate, float basePitch, Bundle baseParams) {
        String[] chunks = text.split("(?<=[\\u0900-\\u097F])\\s*(?=[a-zA-Z])|(?<=[a-zA-Z])\\s*(?=[\\u0900-\\u097F])|(?<=[.!?\\n])\\s+"); 
        LanguageIdentifier langId = LanguageIdentification.getClient();
        
        for (String chunk : chunks) {
            if (isCancelled) break; 
            chunk = chunk.trim(); if (chunk.isEmpty()) continue;
            
            String langCode = "en";
            if(chunk.matches(".*[\\u0900-\\u097F].*")) {
                langCode = "hi"; 
            } else if (chunk.length() > 3) {
                try { String dl = Tasks.await(langId.identifyLanguage(chunk)); if (!"und".equals(dl)) langCode = dl; } catch (Exception ignored) {}
            }
            
            routeToEngine(chunk, baseRate, basePitch, baseParams, langCode);
        }
    }

    private void routeToEngine(String chunk, float baseRate, float basePitch, Bundle baseParams, String langCode) {
        if (isCancelled) return;
        JSONObject langConfig = findLanguageConfig(langCode, chunk);
        if (langConfig != null) {
            String engPkg = langConfig.optString("engine_pkg", "");
            if (!"disabled".equals(engPkg)) {
                float lRate = baseRate;
                float lPitch = basePitch;
                if (prefs.getBoolean("force_rate", false)) lRate = (float) langConfig.optDouble("rate", baseRate);
                if (prefs.getBoolean("force_pitch", false)) lPitch = (float) langConfig.optDouble("pitch", basePitch);
                
                Bundle cParams = new Bundle(baseParams); 
                cParams.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, langConfig.optInt("volume", 100) / 100.0f);
                TextToSpeech targetTts = getOrCreateEngine(engPkg);
                
                if (targetTts != null) {
                    applyVoiceAndLocale(targetTts, langCode, langConfig.optString("voice_variant", "Default"));
                    speakOnEngineAndWait(targetTts, true, chunk, lRate, lPitch, cParams, langCode, engPkg);
                    return;
                }
            }
        }
        speakOnEngineAndWait(primaryTts, isPrimaryReady, chunk, baseRate, basePitch, baseParams, langCode, currentPrimaryEngine);
    }

    private void speakOnEngineAndWait(TextToSpeech tts, boolean isReady, String text, float rate, float pitch, Bundle params, String langCode, String enginePkg) {
        if (tts != null && isReady && !isCancelled) {
            try {
                if (langCode != null && !langCode.isEmpty()) { try { tts.setLanguage(Locale.forLanguageTag(langCode)); } catch (Throwable ignored) {} }
                tts.setSpeechRate(rate); tts.setPitch(pitch);
                Bundle safeParams = params != null ? new Bundle(params) : new Bundle();
                
                // 🔥 1000% STRICT ACCESSIBILITY AUDIO STREAM FIX
                safeParams.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ACCESSIBILITY);
                safeParams.putString(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_ACCESSIBILITY));
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build();
                    try { tts.setAudioAttributes(attrs); } catch (Throwable ignored) {}
                }
                
                // 🔥 THE MAGIC LATCH: कीबोर्ड की स्पेलिंग और छोटे बटन्स कभी नहीं कटेंगे
                String utteranceId = "SP_" + System.currentTimeMillis();
                currentLatch = new CountDownLatch(1);
                
                tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                    @Override public void onStart(String s) {}
                    @Override public void onDone(String s) { if(s.equals(utteranceId) && currentLatch != null) currentLatch.countDown(); }
                    @Override public void onError(String s) { if(s.equals(utteranceId) && currentLatch != null) currentLatch.countDown(); }
                });
                
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, safeParams, utteranceId);
                
                // TalkBack को तब तक रोके रखेगा जब तक आवाज़ पूरी तरह खत्म न हो जाए
                try {
                    currentLatch.await(8, TimeUnit.SECONDS); 
                } catch (Exception ignored) {}
                currentLatch = null;

            } catch (Throwable ignored) {}
        }
    }

    private synchronized TextToSpeech getOrCreateEngine(String pkg) {
        if (pkg == null || pkg.isEmpty() || "disabled".equals(pkg)) return null;
        if (pkg.equals(currentPrimaryEngine)) return primaryTts;
        if (pkg.equals(currentSecondaryEngine)) return secondaryTts;
        if (extraEngines.containsKey(pkg)) return extraEngines.get(pkg);
        TextToSpeech engine = new TextToSpeech(this, status -> {}, pkg); extraEngines.put(pkg, engine); return engine;
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
            try { String engName = tts.getDefaultEngine(); if (engName != null && engName.toLowerCase(Locale.US).contains("eloquence")) return; } catch (Throwable ignored) {}
            try { Set<Voice> voices = tts.getVoices(); if (voices != null) { for (Voice v : voices) { if (v.getName().equals(variant)) { tts.setVoice(v); break; } } } } catch (Exception ignored) {}
        }
    }
}
