package com.speechplus.tts;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.SynthesisCallback;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeechService;
import android.speech.tts.Voice;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("SpeechPlusPrefs", MODE_PRIVATE);
        initEngines();
    }

    private synchronized void initEngines() {
        String pEng = prefs.getString("selected_engine", "");
        String sEng = prefs.getString("secondary_engine", "");

        if (primaryTts == null || !pEng.equals(currentPrimaryEngine)) {
            currentPrimaryEngine = pEng;
            isPrimaryReady = false;
            if (primaryTts != null) {
                try { primaryTts.shutdown(); } catch (Exception ignored) {}
            }
            primaryTts = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS) isPrimaryReady = true;
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
                    if (status == TextToSpeech.SUCCESS) isSecondaryReady = true;
                }, sEng);
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
        try { engine.setAudioAttributes(attrs.build()); } catch (Exception ignored) {}
    }

    @Override
    protected int onIsLanguageAvailable(String lang, String country, String variant) {
        return TextToSpeech.LANG_AVAILABLE;
    }

    @Override
    protected String[] onGetLanguage() {
        String langCode = prefs.getString("selected_lang_code", "hin");
        return new String[]{langCode, "", ""};
    }

    @Override
    protected int onLoadLanguage(String lang, String country, String variant) {
        return TextToSpeech.LANG_AVAILABLE;
    }

    @Override
    protected void onStop() {
        if (primaryTts != null) primaryTts.stop();
        if (secondaryTts != null) secondaryTts.stop();
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

        if (text.isEmpty()) {
            callback.start(16000, android.media.AudioFormat.ENCODING_PCM_16BIT, 1);
            callback.done();
            return;
        }

        initEngines();
        applyAudioRouting(primaryTts);
        applyAudioRouting(secondaryTts);

        float speechRate = prefs.getBoolean("force_rate", false) ? prefs.getFloat("rate", 1.0f) : (request.getSpeechRate() / 100.0f);
        float pitch = prefs.getBoolean("force_pitch", false) ? prefs.getFloat("pitch", 1.0f) : (request.getPitch() / 100.0f);

        boolean amplify = prefs.getBoolean("amplify_volume", true);
        float volume = amplify ? 1.0f : (prefs.getInt("volume", 100) / 100.0f);

        int mode = prefs.getInt("tts_mode", 0);
        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume);

        if (mode == 1 || mode == 2) {
            synthesizeSmartDual(text, speechRate, pitch, params);
        } else {
            synthesizeDirect(primaryTts, isPrimaryReady, text, speechRate, pitch, params);
        }

        callback.start(16000, android.media.AudioFormat.ENCODING_PCM_16BIT, 1);
        callback.done();
    }

    private void synthesizeSmartDual(String text, float rate, float pitch, Bundle params) {
        Pattern pattern = Pattern.compile("([a-zA-Z0-9\\s.,!?'-]+)|([^a-zA-Z0-9]+)");
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            String chunk = matcher.group().trim();
            if (chunk.isEmpty()) continue;

            boolean isLatin = chunk.matches("^[a-zA-Z0-9\\s.,!?'-]+$");
            TextToSpeech target = (isLatin || !isSecondaryReady || secondaryTts == null) ? primaryTts : secondaryTts;
            boolean ready = (isLatin || !isSecondaryReady || secondaryTts == null) ? isPrimaryReady : isSecondaryReady;

            synthesizeDirect(target, ready, chunk, rate, pitch, params);
        }
    }

    private void synthesizeDirect(TextToSpeech tts, boolean isReady, String text, float rate, float pitch, Bundle params) {
        if (tts != null && isReady) {
            tts.setSpeechRate(rate);
            tts.setPitch(pitch);
            tts.speak(text, TextToSpeech.QUEUE_ADD, params, "SpeechPlus_" + System.currentTimeMillis());
        }
    }

    @Override
    public void onDestroy() {
        if (primaryTts != null) { primaryTts.stop(); primaryTts.shutdown(); }
        if (secondaryTts != null) { secondaryTts.stop(); secondaryTts.shutdown(); }
        super.onDestroy();
    }
}
