package com.speechplus.tts;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.speech.tts.SynthesisCallback;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeechService;
import android.speech.tts.UtteranceProgressListener;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

public class SpeechPlusService extends TextToSpeechService {
    private TextToSpeech internalTts;
    private boolean isReady = false;
    private SharedPreferences prefs;
    private CountDownLatch currentLatch;

    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("SpeechPlusPrefs", Context.MODE_PRIVATE);
        internalTts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                setupUtteranceListener();
                isReady = true;
            }
        });
    }

    private void setupUtteranceListener() {
        internalTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            public void onStart(String utteranceId) {}
            public void onDone(String utteranceId) { releaseLatch(); }
            public void onError(String utteranceId) { releaseLatch(); }
            public void onStop(String utteranceId, boolean interrupted) { releaseLatch(); }
        });
    }

    private void releaseLatch() {
        if (currentLatch != null) {
            currentLatch.countDown();
        }
    }

    protected void onSynthesis(SynthesisRequest request, SynthesisCallback callback) {
        if (!isReady || internalTts == null) {
            callback.error();
            return;
        }
        String text = request.getCharSequenceText().toString();
        
        if (prefs.getBoolean("strip_attributes", false)) {
            text = text.replaceAll("[^a-zA-Z0-9.,!? ]", "");
        }
        if (prefs.getBoolean("force_pitch", false)) {
            internalTts.setPitch(prefs.getInt("voice_pitch", 100) / 100.0f);
        }
        if (prefs.getBoolean("force_rate", false)) {
            internalTts.setSpeechRate(prefs.getInt("voice_rate", 100) / 100.0f);
        }

        callback.start(16000, AudioFormat.ENCODING_PCM_16BIT, 1);
        currentLatch = new CountDownLatch(1);
        String utteranceId = "utter_msg_" + System.currentTimeMillis();
        internalTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        
        try {
            currentLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        callback.done();
    }

    protected void onStop() {
        if (internalTts != null) {
            internalTts.stop();
        }
        releaseLatch();
    }

    protected String[] onGetLanguage() {
        return new String[]{"eng", "USA", ""};
    }

    protected int onIsLanguageAvailable(String lang, String country, String variant) {
        if (internalTts != null) {
            return internalTts.isLanguageAvailable(new Locale(lang, country, variant));
        }
        return TextToSpeech.LANG_AVAILABLE;
    }

    protected int onLoadLanguage(String lang, String country, String variant) {
        if (internalTts != null) {
            return internalTts.setLanguage(new Locale(lang, country, variant));
        }
        return TextToSpeech.LANG_AVAILABLE;
    }

    public void onDestroy() {
        if (internalTts != null) {
            internalTts.stop();
            internalTts.shutdown();
        }
        super.onDestroy();
    }
}
