package com.speechplus.tts;

import android.speech.tts.SynthesisCallback;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeechService;
import android.media.AudioFormat;
import java.util.Locale;

public class SpeechPlusService extends TextToSpeechService {
    private Locale currentLocale = Locale.US;

    @Override
    protected int onIsLanguageAvailable(String lang, String country, String variant) {
        return TextToSpeech.LANG_AVAILABLE;
    }

    @Override
    protected String[] onGetLanguage() {
        return new String[]{currentLocale.getLanguage(), currentLocale.getCountry(), currentLocale.getVariant()};
    }

    @Override
    protected int onLoadLanguage(String lang, String country, String variant) {
        currentLocale = new Locale(lang != null ? lang : "en", country != null ? country : "", variant != null ? variant : "");
        return TextToSpeech.LANG_AVAILABLE;
    }

    @Override
    protected void onStop() {}

    @Override
    protected void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
        CharSequence text = request.getCharSequenceText();
        if (text == null) return;

        callback.start(16000, AudioFormat.ENCODING_PCM_16BIT, 1);
        callback.done();
    }
}
