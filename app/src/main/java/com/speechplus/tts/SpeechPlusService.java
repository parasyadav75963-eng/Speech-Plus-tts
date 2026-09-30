package com.speechplus.tts;

import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Bundle;
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
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                internalTts.setAudioAttributes(audioAttributes);
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
        return new String[]{"hin", "IND", ""};
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

            Bundle params = new Bundle();
            params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ACCESSIBILITY);

            internalTts.speak(text.toString(), TextToSpeech.QUEUE_FLUSH, params, "synth_" + System.currentTimeMillis());
        }
    }
}
