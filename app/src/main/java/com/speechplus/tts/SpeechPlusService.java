package com.speechplus.tts;

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

    private Locale currentLocale = Locale.US;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    protected int onIsLanguageAvailable(String lang, String country, String variant) {
        return TextToSpeech.LANG_AVAILABLE;
    }

    @Override
    protected String[] onGetLanguage() {
        return new String[]{
            currentLocale.getLanguage(),
            currentLocale.getCountry(),
            currentLocale.getVariant()
        };
    }

    @Override
    protected int onLoadLanguage(String lang, String country, String variant) {
        currentLocale = new Locale(
            lang != null ? lang : "en",
            country != null ? country : "",
            variant != null ? variant : ""
        );
        return TextToSpeech.LANG_AVAILABLE;
    }

    @Override
    protected void onStop() {
        // स्पीच रोकने के लिए
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
        if (text == null) {
            return;
        }

        int sampleRate = 16000;
        callback.start(sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1);

        // क्रैश से बचने के लिए न्यूनतम शांत (Silence) बफर भेजें
        byte[] buffer = new byte[3200];
        callback.audioAvailable(buffer, 0, buffer.length);

        callback.done();
    }
}
