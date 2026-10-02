package com.speechplus.tts;

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

public class SpeechPlusService extends TextToSpeechService {

    private TextToSpeech primaryTts;
    private TextToSpeech secondaryTts;
    private SharedPreferences prefs;

    private boolean isPrimaryReady = false;
    private boolean isSecondaryReady = false;

    private String currentPrimaryEngine = "";
    private String currentSecondaryEngine = "";
    private int lastAudioRouting = -1;

    // Matches Regional characters (Devanagari, Urdu/Arabic, Tamil, Telugu, Bengali, Gurmukhi, etc.)
    private static final Pattern REGIONAL_PATTERN = Pattern.compile("[^\\x00-\\x7F\\u00A0-\\u00FF]");

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("SpeechPlusPrefs", MODE_PRIVATE);
        initEngines();
    }

    private synchronized void initEngines() {
        if (prefs == null) {
            prefs = getSharedPreferences("SpeechPlusPrefs", MODE_PRIVATE);
        }

        String pEng = prefs.getString("primary_engine", "");
        String sEng = prefs.getString("secondary_engine", "");

        if (primaryTts == null || !pEng.equals(currentPrimaryEngine)) {
            currentPrimaryEngine = pEng;
            isPrimaryReady = false;
            if (primaryTts != null) {
                try { primaryTts.shutdown(); } catch (Exception ignored) {}
            }
            primaryTts = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    isPrimaryReady = true;
                    applyAudioRouting(primaryTts);
                }
            }, pEng.isEmpty() ? null : pEng);
        }

        if (secondaryTts == null || !sEng.equals(currentSecondaryEngine)) {
            currentSecondaryEngine = sEng;
            isSecondaryReady = false;
            if (secondaryTts != null) {
                try { secondaryTts.shutdown(); } catch (Exception ignored) {}
            }
            if (!sEng.isEmpty()) {
                secondaryTts = new TextToSpeech(this, status -> {
                    if (status == TextToSpeech.SUCCESS) {
                        isSecondaryReady = true;
                        applyAudioRouting(secondaryTts);
                    }
                }, sEng);
            } else {
                secondaryTts = null;
            }
        }
    }

    private void applyAudioRouting(TextToSpeech engine) {
        if (engine == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        int routing = prefs.getInt("audio_routing", 0);
        AudioAttributes.Builder attrs = new AudioAttributes.Builder();
        if (routing == 1) {
            attrs.setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                 .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH);
        } else {
            attrs.setUsage(AudioAttributes.USAGE_MEDIA)
                 .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH);
        }
        try {
            engine.setAudioAttributes(attrs.build());
        } catch (Exception ignored) {}
    }

    private boolean containsRegional(String text) {
        if (text == null) return false;
        return REGIONAL_PATTERN.matcher(text).find();
    }

    @Override
    protected int onIsLanguageAvailable(String lang, String country, String variant) {
        return TextToSpeech.LANG_AVAILABLE;
    }

    @Override
    protected String[] onGetLanguage() {
        String langCode = prefs != null ? prefs.getString("selected_lang_code", "hin") : "hin";
        return new String[]{langCode, "", ""};
    }

    @Override
    protected int onLoadLanguage(String lang, String country, String variant) {
        return TextToSpeech.LANG_AVAILABLE;
    }

    @Override
    protected void onStop() {
        // Instant stop on TalkBack cancel
        if (primaryTts != null) {
            try { primaryTts.stop(); } catch (Exception ignored) {}
        }
        if (secondaryTts != null) {
            try { secondaryTts.stop(); } catch (Exception ignored) {}
        }
    }

    @Override
    protected synchronized void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
        String text = "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CharSequence cs = request.getCharSequenceText();
            if (cs != null) text = cs.toString();
        }
        if (text.isEmpty() && request.getText() != null) {
            text = request.getText();
        }

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
            applyAudioRouting(primaryTts);
            applyAudioRouting(secondaryTts);
        }

        boolean forceRate = prefs.getBoolean("force_rate", true);
        float speechRate = forceRate ? prefs.getFloat("rate", 1.0f) : (request.getSpeechRate() / 100.0f);
        if (speechRate <= 0.1f) speechRate = 1.0f;

        boolean forcePitch = prefs.getBoolean("force_pitch", true);
        float pitch = forcePitch ? prefs.getFloat("pitch", 1.0f) : (request.getPitch() / 100.0f);
        if (pitch <= 0.1f) pitch = 1.0f;

        boolean amplify = prefs.getBoolean("amplify_volume", true);
        float volume = amplify ? 1.0f : (prefs.getInt("volume", 100) / 100.0f);

        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, routing == 1 ? AudioManager.STREAM_ACCESSIBILITY : AudioManager.STREAM_MUSIC);
        }

        int mode = prefs.getInt("tts_mode", 0);

        // INSTANT CUT-OFF: Cut off previous speech immediately so swipe has ZERO lag
        if (primaryTts != null) {
            try { primaryTts.stop(); } catch (Exception ignored) {}
        }
        if (secondaryTts != null) {
            try { secondaryTts.stop(); } catch (Exception ignored) {}
        }

        if (mode == 0) {
            // Mode 0: Single Engine Mode
            speakOnEngine(primaryTts, isPrimaryReady, text, speechRate, pitch, params, true);
        } else if (mode == 1) {
            // Mode 1: Dual Language Mode (Latin -> Primary, Regional -> Secondary)
            if (containsRegional(text)) {
                TextToSpeech target = (isSecondaryReady && secondaryTts != null) ? secondaryTts : primaryTts;
                boolean ready = (isSecondaryReady && secondaryTts != null) ? isSecondaryReady : isPrimaryReady;
                speakOnEngine(target, ready, text, speechRate, pitch, params, true);
            } else {
                speakOnEngine(primaryTts, isPrimaryReady, text, speechRate, pitch, params, true);
            }
        } else {
            // Mode 2: Mixed Language Mode (Fast Auto Detect & Routing)
            synthesizeSmartDual(text, speechRate, pitch, params);
        }

        callback.start(16000, AudioFormat.ENCODING_PCM_16BIT, 1);
        callback.done();
    }

    private void synthesizeSmartDual(String text, float rate, float pitch, Bundle params) {
        if (!containsRegional(text)) {
            speakOnEngine(primaryTts, isPrimaryReady, text, rate, pitch, params, true);
            return;
        }

        Pattern pattern = Pattern.compile("([a-zA-Z0-9\\s.,!?'-]+)|([^a-zA-Z0-9]+)");
        Matcher matcher = pattern.matcher(text);

        boolean isFirst = true;
        while (matcher.find()) {
            String chunk = matcher.group().trim();
            if (chunk.isEmpty()) continue;

            boolean isLatin = !containsRegional(chunk);
            TextToSpeech target = (isLatin || !isSecondaryReady || secondaryTts == null) ? primaryTts : secondaryTts;
            boolean ready = (isLatin || !isSecondaryReady || secondaryTts == null) ? isPrimaryReady : isSecondaryReady;

            speakOnEngine(target, ready, chunk, rate, pitch, params, isFirst);
            isFirst = false;
        }
    }

    private void speakOnEngine(TextToSpeech tts, boolean isReady, String text, float rate, float pitch, Bundle params, boolean flush) {
        if (tts != null && isReady) {
            try {
                tts.setSpeechRate(rate);
                tts.setPitch(pitch);
                int queueMode = flush ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
                tts.speak(text, queueMode, params, "SpeechPlus_" + System.currentTimeMillis());
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onDestroy() {
        if (primaryTts != null) {
            try {
                primaryTts.stop();
                primaryTts.shutdown();
            } catch (Exception ignored) {}
        }
        if (secondaryTts != null) {
            try {
                secondaryTts.stop();
                secondaryTts.shutdown();
            } catch (Exception ignored) {}
        }
        super.onDestroy();
    }
}
