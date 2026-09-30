package com.speechplus.tts;

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
import android.speech.tts.Voice;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
        if (internalTts != null && isInitialized) {
            return internalTts.isLanguageAvailable(new Locale(lang, country, variant));
        }
        return TextToSpeech.LANG_AVAILABLE;
    }

    @Override
    protected String[] onGetLanguage() {
        if (internalTts != null && isInitialized && internalTts.getLanguage() != null) {
            Locale loc = internalTts.getLanguage();
            return new String[]{loc.getISO3Language(), loc.getISO3Country(), loc.getVariant()};
        }
        return new String[]{"eng", "USA", ""};
    }

    @Override
    protected int onLoadLanguage(String lang, String country, String variant) {
        if (internalTts != null && isInitialized) {
            return internalTts.setLanguage(new Locale(lang, country, variant));
        }
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
        if (internalTts != null && isInitialized) {
            try {
                Set<Voice> voices = internalTts.getVoices();
                if (voices != null && !voices.isEmpty()) {
                    return new ArrayList<>(voices);
                }
            } catch (Exception ignored) {}
        }
        List<Voice> fallback = new ArrayList<>();
        fallback.add(new Voice("speech_plus_default", Locale.getDefault(), Voice.QUALITY_HIGH, Voice.LATENCY_NORMAL, false, new HashSet<>()));
        return fallback;
    }

    @Override
    public String onGetDefaultVoiceNameFor(String lang, String country, String variant) {
        if (internalTts != null && isInitialized) {
            try {
                Voice v = internalTts.getDefaultVoice();
                if (v != null) return v.getName();
            } catch (Exception ignored) {}
        }
        return "speech_plus_default";
    }

    @Override
    public int onLoadVoice(String name) {
        if (internalTts != null && isInitialized) {
            try {
                Set<Voice> voices = internalTts.getVoices();
                if (voices != null) {
                    for (Voice v : voices) {
                        if (v.getName().equals(name)) {
                            return internalTts.setVoice(v);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
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

        // TalkBack speed synchronization (Priority to TalkBack request speech rate)
        float rate = (float) request.getSpeechRate() / 100.0f;
        if (rate <= 0.0f) {
            SharedPreferences prefs = getSharedPreferences("speech_plus_prefs", MODE_PRIVATE);
            rate = prefs.getFloat("rate", 1.0f);
        }

        float pitch = (float) request.getPitch() / 100.0f;
        if (pitch <= 0.0f) {
            SharedPreferences prefs = getSharedPreferences("speech_plus_prefs", MODE_PRIVATE);
            pitch = prefs.getFloat("pitch", 1.0f);
        }

        callback.start(16000, android.media.AudioFormat.ENCODING_PCM_16BIT, 1);

        if (internalTts != null && isInitialized) {
            internalTts.setSpeechRate(rate);
            internalTts.setPitch(pitch);

            // Handle language/locale requested by TalkBack for character/word reading
            String reqLang = request.getLanguage();
            if (reqLang != null && !reqLang.isEmpty()) {
                try {
                    internalTts.setLanguage(new Locale(reqLang, request.getCountry() != null ? request.getCountry() : ""));
                } catch (Exception ignored) {}
            }

            Bundle params = new Bundle();
            params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ACCESSIBILITY);

            internalTts.speak(text.toString(), TextToSpeech.QUEUE_FLUSH, params, "req_" + System.currentTimeMillis());
        }

        callback.done();
    }
}
