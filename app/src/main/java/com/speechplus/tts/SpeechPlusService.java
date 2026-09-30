package com.speechplus.tts;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.SynthesisCallback;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeechService;

public class SpeechPlusService extends TextToSpeechService {

    private TextToSpeech internalTts;
    private volatile boolean isInitialized = false;
    private Handler mainHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        initTargetEngine();
    }

    private void initTargetEngine() {
        mainHandler.post(() -> {
            SharedPreferences prefs = getSharedPreferences("speech_plus_prefs", MODE_PRIVATE);
            String targetEngine = prefs.getString("selected_engine", null);

            TextToSpeech.OnInitListener listener = status -> {
                if (status == TextToSpeech.SUCCESS) {
                    isInitialized = true;
                    try {
                        AudioAttributes attrs = new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build();
                        internalTts.setAudioAttributes(attrs);
                    } catch (Exception ignored) {}
                }
            };

            try {
                if (targetEngine != null && !targetEngine.trim().isEmpty()) {
                    internalTts = new TextToSpeech(getApplicationContext(), listener, targetEngine);
                } else {
                    internalTts = new TextToSpeech(getApplicationContext(), listener);
                }
            } catch (Exception e) {
                internalTts = new TextToSpeech(getApplicationContext(), listener);
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
    public String onGetDefaultVoiceNameFor(String lang, String country, String variant) {
        return "speech_plus_default";
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

        // Notify TalkBack that synthesis has started
        callback.start(16000, android.media.AudioFormat.ENCODING_PCM_16BIT, 1);

        if (internalTts != null && isInitialized) {
            internalTts.setSpeechRate(rate);
            internalTts.setPitch(pitch);

            Bundle params = new Bundle();
            params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ACCESSIBILITY);

            internalTts.speak(text.toString(), TextToSpeech.QUEUE_FLUSH, params, "req_" + System.currentTimeMillis());
        }

        // Complete callback so TalkBack does not hang or fall back
        callback.done();
    }
}
