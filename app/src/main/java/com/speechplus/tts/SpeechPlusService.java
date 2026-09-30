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

    private TextToSpeech primaryTts;
    private TextToSpeech secondaryTts;
    private volatile boolean isPrimaryInit = false;
    private volatile boolean isSecondaryInit = false;
    private Handler mainHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        initEngines();
    }

    private void initEngines() {
        mainHandler.post(() -> {
            SharedPreferences prefs = getSharedPreferences("speech_plus_prefs", MODE_PRIVATE);
            String targetEngine = prefs.getString("selected_engine", null);
            String secondaryEngine = prefs.getString("secondary_engine", targetEngine);

            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();

            TextToSpeech.OnInitListener primaryListener = status -> {
                if (status == TextToSpeech.SUCCESS) {
                    isPrimaryInit = true;
                    try { primaryTts.setAudioAttributes(attrs); } catch (Exception ignored) {}
                }
            };

            TextToSpeech.OnInitListener secondaryListener = status -> {
                if (status == TextToSpeech.SUCCESS) {
                    isSecondaryInit = true;
                    try { secondaryTts.setAudioAttributes(attrs); } catch (Exception ignored) {}
                }
            };

            try {
                if (targetEngine != null && !targetEngine.trim().isEmpty()) {
                    primaryTts = new TextToSpeech(getApplicationContext(), primaryListener, targetEngine);
                } else {
                    primaryTts = new TextToSpeech(getApplicationContext(), primaryListener);
                }
            } catch (Exception e) {
                primaryTts = new TextToSpeech(getApplicationContext(), primaryListener);
            }

            try {
                if (secondaryEngine != null && !secondaryEngine.trim().isEmpty()) {
                    secondaryTts = new TextToSpeech(getApplicationContext(), secondaryListener, secondaryEngine);
                } else {
                    secondaryTts = primaryTts;
                    isSecondaryInit = true;
                }
            } catch (Exception e) {
                secondaryTts = primaryTts;
                isSecondaryInit = true;
            }
        });
    }

    @Override
    public void onDestroy() {
        if (primaryTts != null) {
            primaryTts.stop();
            primaryTts.shutdown();
        }
        if (secondaryTts != null && secondaryTts != primaryTts) {
            secondaryTts.stop();
            secondaryTts.shutdown();
        }
        super.onDestroy();
    }

    @Override
    protected int onIsLanguageAvailable(String lang, String country, String variant) {
        if (primaryTts != null && isPrimaryInit) {
            return primaryTts.isLanguageAvailable(new Locale(lang, country, variant));
        }
        return TextToSpeech.LANG_AVAILABLE;
    }

    @Override
    protected String[] onGetLanguage() {
        if (primaryTts != null && isPrimaryInit && primaryTts.getLanguage() != null) {
            Locale loc = primaryTts.getLanguage();
            return new String[]{loc.getISO3Language(), loc.getISO3Country(), loc.getVariant()};
        }
        return new String[]{"hin", "IND", ""};
    }

    @Override
    protected int onLoadLanguage(String lang, String country, String variant) {
        if (primaryTts != null && isPrimaryInit) {
            return primaryTts.setLanguage(new Locale(lang, country, variant));
        }
        return TextToSpeech.LANG_AVAILABLE;
    }

    @Override
    protected void onStop() {
        if (primaryTts != null) primaryTts.stop();
        if (secondaryTts != null && secondaryTts != primaryTts) secondaryTts.stop();
    }

    @Override
    public List<Voice> onGetVoices() {
        if (primaryTts != null && isPrimaryInit) {
            try {
                Set<Voice> voices = primaryTts.getVoices();
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
        if (primaryTts != null && isPrimaryInit) {
            try {
                Voice v = primaryTts.getDefaultVoice();
                if (v != null) return v.getName();
            } catch (Exception ignored) {}
        }
        return "speech_plus_default";
    }

    @Override
    public int onLoadVoice(String name) {
        if (primaryTts != null && isPrimaryInit) {
            try {
                Set<Voice> voices = primaryTts.getVoices();
                if (voices != null) {
                    for (Voice v : voices) {
                        if (v.getName().equals(name)) {
                            return primaryTts.setVoice(v);
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
        CharSequence textSeq = request.getCharSequenceText();
        if (textSeq == null || textSeq.length() == 0) {
            return;
        }

        String text = textSeq.toString();

        SharedPreferences prefs = getSharedPreferences("speech_plus_prefs", MODE_PRIVATE);
        boolean forceRate = prefs.getBoolean("force_rate", false);
        boolean forcePitch = prefs.getBoolean("force_pitch", false);
        int mode = prefs.getInt("tts_mode", 0); // 0: Single, 1: Dual, 2: Mix

        float rate;
        if (forceRate) {
            rate = prefs.getFloat("rate", 1.0f);
        } else {
            rate = (float) request.getSpeechRate() / 100.0f;
            if (rate <= 0.0f) rate = prefs.getFloat("rate", 1.0f);
        }

        float pitch;
        if (forcePitch) {
            pitch = prefs.getFloat("pitch", 1.0f);
        } else {
            pitch = (float) request.getPitch() / 100.0f;
            if (pitch <= 0.0f) pitch = prefs.getFloat("pitch", 1.0f);
        }

        // Mode-based engine routing
        TextToSpeech selectedTts = primaryTts;
        if (mode == 1 || mode == 2) {
            boolean hasLatin = false;
            boolean hasIndic = false;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
                if (block == Character.UnicodeBlock.BASIC_LATIN || block == Character.UnicodeBlock.LATIN_1_SUPPLEMENT) {
                    if (Character.isLetter(c)) hasLatin = true;
                } else if (block == Character.UnicodeBlock.DEVANAGARI || block == Character.UnicodeBlock.BENGALI || 
                           block == Character.UnicodeBlock.TAMIL || block == Character.UnicodeBlock.TELUGU) {
                    hasIndic = true;
                }
            }
            if (hasLatin && !hasIndic && secondaryTts != null && isSecondaryInit) {
                selectedTts = secondaryTts;
            }
        }

        callback.start(16000, android.media.AudioFormat.ENCODING_PCM_16BIT, 1);

        if (selectedTts != null && (selectedTts == primaryTts ? isPrimaryInit : isSecondaryInit)) {
            selectedTts.setSpeechRate(rate);
            selectedTts.setPitch(pitch);

            // Fast character echo for typing
            if (text.length() == 1) {
                selectedTts.setSpeechRate(rate * 1.05f);
            }

            String reqLang = request.getLanguage();
            if (reqLang != null && !reqLang.isEmpty()) {
                try {
                    selectedTts.setLanguage(new Locale(reqLang, request.getCountry() != null ? request.getCountry() : ""));
                } catch (Exception ignored) {}
            }

            Bundle params = new Bundle();
            params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ACCESSIBILITY);
            selectedTts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "sp_" + System.currentTimeMillis());
        }

        callback.done();
    }
}
