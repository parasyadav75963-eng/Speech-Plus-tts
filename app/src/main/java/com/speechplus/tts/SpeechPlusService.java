package com.speechplus.tts;

import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.speech.tts.SynthesisCallback;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeechService;
import android.speech.tts.Voice;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SpeechPlusService extends TextToSpeechService {

    private TextToSpeech internalTts;
    private boolean isInitialized = false;

    @Override
    public void onCreate() {
        super.onCreate();
        internalTts = new TextToSpeech(getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true;
            }
        });
    }

    @Override
    public void onDestroy() {
        if (internalTts != null) {
            internalTts.stop();
            internalTts.shutdown();
        }
        super.onDestroy();
    }

    @Override
    protected int onIsLanguageAvailable(String lang, String country, String variant) {
        return TextToSpeech.LANG_AVAILABLE;
    }

    @Override
    protected String[] onGetLanguage() {
        return new String[]{"eng", "USA", ""};
    }

    @Override
    protected int onLoadLanguage(String lang, String country, String variant) {
        return TextToSpeech.LANG_AVAILABLE;
    }

    @Override
    protected void onStop() {
        if (internalTts != null) {
            internalTts.stop();
        }
    }

    @Override
    public List<Voice> onGetVoices() {
        List<Voice> voices = new ArrayList<>();
        Set<String> features = new HashSet<>();
        voices.add(new Voice("en-us-speechplus", Locale.US, Voice.QUALITY_NORMAL, Voice.LATENCY_NORMAL, false, features));
        return voices;
    }

    @Override
    public String onGetDefaultVoiceNameFor(String lang, String country, String variant) {
        return "en-us-speechplus";
    }

    @Override
    public int onLoadVoice(String name) {
        return TextToSpeech.SUCCESS;
    }

    @Override
    public int onIsValidVoiceName(String name) {
        return TextToSpeech.SUCCESS;
    }

    @Override
    protected void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
        CharSequence text = request.getCharSequenceText();
        if (text == null || text.length() == 0) {
            return;
        }

        SharedPreferences prefs = getSharedPreferences("speech_plus_prefs", MODE_PRIVATE);
        float rate = prefs.getFloat("rate", 1.0f);
        float pitch = prefs.getFloat("pitch", 1.0f);

        if (internalTts != null && isInitialized) {
            internalTts.setSpeechRate(rate);
            internalTts.setPitch(pitch);
            internalTts.speak(text.toString(), TextToSpeech.QUEUE_FLUSH, null, "synth_" + System.currentTimeMillis());
        }

        // TalkBack को फीडबैक पूरा करने का सिग्नल दें
        callback.start(16000, AudioFormat.ENCODING_PCM_16BIT, 1);
        callback.done();
    }
}
