package com.speechplus.tts;

import android.content.SharedPreferences;
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

public class SpeechPlusService extends TextToSpeechService {
    private TextToSpeech primaryTts;
    private TextToSpeech secondaryTts;
    private SharedPreferences prefs;
    private boolean isPrimaryReady = false;
    private boolean isSecondaryReady = false;
    private String currentPrimaryEngine = "";
    private String currentSecondaryEngine = "";
    private static final Pattern REGIONAL_PATTERN = Pattern.compile("[^\\x00-\\x7F\\u00A0-\\u00FF]");

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
                if (status == TextToSpeech.SUCCESS) isPrimaryReady = true;
            }, pEng.isEmpty() ? null : pEng);
        }

        if (secondaryTts == null || !sEng.equals(currentSecondaryEngine)) {
            currentSecondaryEngine = sEng;
            isSecondaryReady = false;
            if (secondaryTts != null) { try { secondaryTts.shutdown(); } catch (Exception ignored) {} }
            if (!sEng.isEmpty()) {
                secondaryTts = new TextToSpeech(this, status -> {
                    if (status == TextToSpeech.SUCCESS) isSecondaryReady = true;
                }, sEng);
            } else { secondaryTts = null; }
        }
    }

    private boolean containsRegional(String text) {
        return text != null && REGIONAL_PATTERN.matcher(text).find();
    }

    // 100% FIX FOR TALKBACK CRASH
    @Override
    protected int onIsLanguageAvailable(String lang, String country, String variant) {
        return TextToSpeech.LANG_AVAILABLE; 
    }

    @Override
    protected String[] onGetLanguage() {
        return new String[]{"hin", "IND", ""};
    }

    @Override
    protected int onLoadLanguage(String lang, String country, String variant) {
        return TextToSpeech.LANG_AVAILABLE;
    }

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

        // ADVANCED SETTINGS LOGIC (SYNC / DESYNC)
        boolean forceRate = prefs.getBoolean("force_rate", true);
        boolean forcePitch = prefs.getBoolean("force_pitch", true);
        int routing = prefs.getInt("audio_routing", 0);

        // If force is TRUE -> Use App Settings. If FALSE -> Sync with TalkBack
        float reqRate = request.getSpeechRate() / 100.0f;
        float reqPitch = request.getPitch() / 100.0f;
        float appRate = (prefs.getInt("voice_tab_rate_progress", 20) / 20.0f);
        float appPitch = (prefs.getInt("voice_tab_pitch_progress", 20) / 20.0f);
        
        if (appRate <= 0.1f) appRate = 0.1f;
        if (appPitch <= 0.1f) appPitch = 0.1f;

        float finalRate = forceRate ? appRate : reqRate;
        float finalPitch = forcePitch ? appPitch : reqPitch;

        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, prefs.getInt("voice_tab_volume", 100) / 100.0f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // STREAM ROUTING FIX (Media vs Accessibility)
            params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, routing == 1 ? AudioManager.STREAM_ACCESSIBILITY : AudioManager.STREAM_MUSIC);
        }

        int mode = prefs.getInt("tts_mode", 0);
        onStop(); // INSTANT CUTOFF FOR ZERO LAG

        if (mode == 0) {
            // MODE 0: SINGLE ENGINE
            speakOnEngine(primaryTts, isPrimaryReady, text, finalRate, finalPitch, params);
        } else if (mode == 1) {
            // MODE 1: AUTO DETECT LANGUAGE (Regex Chunking)
            synthesizeSmartDual(text, finalRate, finalPitch, params);
        } else {
            // MODE 2: MIXED REGIONAL
            if (containsRegional(text) && isSecondaryReady) {
                speakOnEngine(secondaryTts, isSecondaryReady, text, finalRate, finalPitch, params);
            } else {
                speakOnEngine(primaryTts, isPrimaryReady, text, finalRate, finalPitch, params);
            }
        }
        
        callback.start(16000, AudioFormat.ENCODING_PCM_16BIT, 1);
        callback.done();
    }

    private void synthesizeSmartDual(String text, float rate, float pitch, Bundle params) {
        if (!containsRegional(text)) {
            speakOnEngine(primaryTts, isPrimaryReady, text, rate, pitch, params);
            return;
        }
        // Splits Hindi and English text perfectly
        Pattern pattern = Pattern.compile("([a-zA-Z0-9\\s.,!?'-]+)|([^a-zA-Z0-9]+)");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String chunk = matcher.group().trim();
            if (chunk.isEmpty()) continue;
            boolean isLatin = !containsRegional(chunk);
            TextToSpeech target = (isLatin || !isSecondaryReady || secondaryTts == null) ? primaryTts : secondaryTts;
            boolean ready = (isLatin || !isSecondaryReady || secondaryTts == null) ? isPrimaryReady : isSecondaryReady;
            speakOnEngine(target, ready, chunk, rate, pitch, params);
        }
    }

    private void speakOnEngine(TextToSpeech tts, boolean isReady, String text, float rate, float pitch, Bundle params) {
        if (tts != null && isReady) {
            try {
                tts.setSpeechRate(rate);
                tts.setPitch(pitch);
                tts.speak(text, TextToSpeech.QUEUE_ADD, params, "SP_" + System.currentTimeMillis());
            } catch (Exception ignored) {}
        }
    }
}
