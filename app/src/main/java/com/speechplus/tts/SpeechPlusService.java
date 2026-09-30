package com.speechplus.tts;

import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.os.Bundle;
import android.speech.tts.SynthesisCallback;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeechService;

public class SpeechPlusService extends TextToSpeechService {

    private TextToSpeech primaryTts;
    private TextToSpeech secondaryTts;
    private SharedPreferences prefs;
    private boolean isPrimaryReady = false;
    private boolean isSecondaryReady = false;

    @Override
    public void onCreate() {
        prefs = getSharedPreferences("speech_plus_prefs", MODE_PRIVATE);
        initEngines();
        super.onCreate();
    }

    private void initEngines() {
        String pEng = prefs.getString("selected_engine", "");
        if (!pEng.isEmpty()) {
            primaryTts = new TextToSpeech(this, status -> isPrimaryReady = (status == TextToSpeech.SUCCESS), pEng);
        } else {
            primaryTts = new TextToSpeech(this, status -> isPrimaryReady = (status == TextToSpeech.SUCCESS));
        }

        String sEng = prefs.getString("secondary_engine", "");
        if (!sEng.isEmpty()) {
            secondaryTts = new TextToSpeech(this, status -> isSecondaryReady = (status == TextToSpeech.SUCCESS), sEng);
        }
    }

    @Override
    protected String[] onGetLanguage() {
        return new String[]{"hin", "IND", ""};
    }

    @Override
    protected int onIsLanguageAvailable(String lang, String country, String variant) {
        return TextToSpeech.LANG_AVAILABLE;
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
    protected void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
        String text = request.getCharSequenceText() != null ? request.getCharSequenceText().toString() : "";
        if (text.trim().isEmpty()) {
            callback.start(16000, AudioAttributes.CONTENT_TYPE_SPEECH, 1);
            callback.done();
            return;
        }

        text = sanitizeTypingText(text);

        int mode = prefs.getInt("tts_mode", 0);
        float rate = prefs.getBoolean("force_rate", false) ? prefs.getFloat("rate", 1.0f) : (request.getSpeechRate() / 100.0f);
        float pitch = prefs.getBoolean("force_pitch", false) ? prefs.getFloat("pitch", 1.0f) : (request.getPitch() / 100.0f);

        TextToSpeech targetTts = primaryTts;
        if ((mode == 1 || mode == 2) && isSecondaryReady && containsLatin(text)) {
            targetTts = secondaryTts;
        }

        if (targetTts != null) {
            targetTts.setSpeechRate(Math.max(0.5f, rate));
            targetTts.setPitch(Math.max(0.5f, pitch));
            Bundle params = new Bundle();
            targetTts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "service_utt");
        }

        callback.start(16000, AudioAttributes.CONTENT_TYPE_SPEECH, 1);
        callback.done();
    }

    private String sanitizeTypingText(String text) {
        if (text.length() == 1) return text;
        if (text.equalsIgnoreCase("space")) return "स्पेस";
        if (text.equalsIgnoreCase("delete") || text.equalsIgnoreCase("backspace")) return "डिलीट";
        if (text.equalsIgnoreCase("enter")) return "एंटर";
        if (text.equalsIgnoreCase("voice input")) return "वॉइस इनपुट";
        if (text.contains(" as in ")) {
            return text.split(" as in ")[0].trim();
        }
        return text;
    }

    private boolean containsLatin(String text) {
        for (char c : text.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) return true;
        }
        return false;
    }

    @Override
    public void onDestroy() {
        if (primaryTts != null) { primaryTts.stop(); primaryTts.shutdown(); }
        if (secondaryTts != null) { secondaryTts.stop(); secondaryTts.shutdown(); }
        super.onDestroy();
    }
}
