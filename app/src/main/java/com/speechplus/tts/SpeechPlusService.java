package com.speechplus.tts;

import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.SynthesisCallback;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeechService;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.android.gms.tasks.Tasks;

public class SpeechPlusService extends TextToSpeechService {
    private TextToSpeech primaryTts;
    private TextToSpeech secondaryTts;
    private SharedPreferences prefs;
    private String currentPrimaryEngine = "";
    private String currentSecondaryEngine = "";
    private final Map<String, TextToSpeech> extraEngines = new ConcurrentHashMap<>();
    private final Map<String, CountDownLatch> engineInitLatches = new ConcurrentHashMap<>();

    private volatile boolean isStopped = false;
    private volatile CountDownLatch activeSynthLatch = null;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("SpeechPlusPrefs", MODE_PRIVATE);
        initEngines();
    }

    private String getFallbackEnginePkg() {
        try {
            Intent intent = new Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE);
            List<ResolveInfo> resolveInfos = getPackageManager().queryIntentServices(intent, 0);
            String firstOther = "";
            if (resolveInfos != null) {
                for (ResolveInfo info : resolveInfos) {
                    String pkg = info.serviceInfo.packageName;
                    if (!getPackageName().equals(pkg)) {
                        if ("com.google.android.tts".equals(pkg)) return pkg;
                        if (firstOther.isEmpty()) firstOther = pkg;
                    }
                }
            }
            if (!firstOther.isEmpty()) return firstOther;
        } catch (Exception ignored) {}
        return "com.google.android.tts";
    }

    private void enforceAccessibilityStream(TextToSpeech tts) {
        if (tts == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
            try { tts.setAudioAttributes(attrs); } catch (Exception ignored) {}
        }
    }

    private synchronized void initEngines() {
        if (prefs == null) prefs = getSharedPreferences("SpeechPlusPrefs", MODE_PRIVATE);
        String pEng = prefs.getString("primary_engine", "");
        if (pEng == null || pEng.isEmpty() || pEng.equals(getPackageName())) {
            pEng = getFallbackEnginePkg();
        }
        String sEng = prefs.getString("secondary_engine", "");

        if (primaryTts == null || !pEng.equals(currentPrimaryEngine)) {
            currentPrimaryEngine = pEng;
            if (primaryTts != null) { try { primaryTts.shutdown(); } catch (Exception ignored) {} }
            final String targetPkg = pEng;
            final CountDownLatch latch = new CountDownLatch(1);
            engineInitLatches.put(targetPkg, latch);
            primaryTts = new TextToSpeech(this, status -> {
                enforceAccessibilityStream(primaryTts);
                latch.countDown();
            }, targetPkg);
        }

        if (secondaryTts == null || !sEng.equals(currentSecondaryEngine)) {
            currentSecondaryEngine = sEng;
            if (secondaryTts != null) { try { secondaryTts.shutdown(); } catch (Exception ignored) {} }
            if (sEng != null && !sEng.isEmpty() && !sEng.equals(getPackageName())) {
                final String targetPkg = sEng;
                final CountDownLatch latch = new CountDownLatch(1);
                engineInitLatches.put(targetPkg, latch);
                secondaryTts = new TextToSpeech(this, status -> {
                    enforceAccessibilityStream(secondaryTts);
                    latch.countDown();
                }, targetPkg);
            } else {
                secondaryTts = null;
            }
        }

        Set<String> keys = prefs.getStringSet("configured_lang_keys", new HashSet<>());
        for (String k : keys) {
            try {
                JSONObject obj = new JSONObject(prefs.getString(k, ""));
                String ePkg = obj.optString("engine_pkg", "");
                if (!ePkg.isEmpty() && !"disabled".equals(ePkg) && !ePkg.equals(getPackageName())) {
                    getOrCreateEngine(ePkg);
                }
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
        isStopped = false;
        CharSequence cs = request.getCharSequenceText();
        String rawText = cs != null ? cs.toString() : request.getText();

        if (rawText == null || rawText.trim().isEmpty()) {
            callback.start(24000, AudioFormat.ENCODING_PCM_16BIT, 1);
            callback.done();
            return;
        }

        initEngines();
        String text = rawText.trim();

        // 1. Punctuation Rules Apply
        int punctLevel = prefs.getInt("punctuation_level", 0);
        boolean hasHindi = containsDevanagari(text);
        text = applyPunctuationRules(text, punctLevel, hasHindi);
        
        // 2. Number Formatting Apply
        int numFormat = prefs.getInt("num_format", 0);
        if (numFormat != 0) {
            text = formatNumbers(text, numFormat);
        }

        if (text.trim().isEmpty()) {
            callback.start(24000, AudioFormat.ENCODING_PCM_16BIT, 1);
            callback.done();
            return;
        }

        boolean forceRate = prefs.getBoolean("force_rate", false);
        boolean forcePitch = prefs.getBoolean("force_pitch", false);

        float speechRate = forceRate
            ? Math.max(0.1f, Math.min(prefs.getInt("voice_tab_rate_progress", 50) / 50.0f, 3.0f))
            : (request.getSpeechRate() > 0 ? request.getSpeechRate() / 100.0f : 1.0f);
        float pitch = forcePitch
            ? Math.max(0.1f, Math.min(prefs.getInt("voice_tab_pitch_progress", 50) / 50.0f, 3.0f))
            : (request.getPitch() > 0 ? request.getPitch() / 100.0f : 1.0f);

        StreamState streamState = new StreamState();
        List<String> chunks = splitTextLossless(text);
        LanguageIdentifier langId = LanguageIdentification.getClient();
        int numLangPref = prefs.getInt("num_lang", 0); // 0=Default, 1=Hindi, 2=English

        for (String chunk : chunks) {
            if (isStopped) break;
            String c = chunk.trim();
            if (c.isEmpty()) continue;

            String langCode = "en";
            
            // Number Language override logic
            if (c.matches("^[0-9\\s,.-]+$") && numLangPref != 0) {
                langCode = (numLangPref == 1) ? "hi" : "en";
            } else if (containsDevanagari(c)) {
                langCode = "hi";
            } else if (c.length() > 4) {
                try {
                    String dl = Tasks.await(langId.identifyLanguage(c), 120, TimeUnit.MILLISECONDS);
                    if (dl != null && !"und".equals(dl)) langCode = dl;
                } catch (Exception ignored) {}
            }
            routeAndSynthesize(c, speechRate, pitch, langCode, callback, streamState);
        }

        if (!streamState.started) {
            callback.start(24000, AudioFormat.ENCODING_PCM_16BIT, 1);
        }
        callback.done();
    }

    private static class StreamState {
        boolean started = false;
        int sampleRate = 24000;
    }

    private String formatNumbers(String text, int formatType) {
        StringBuilder sb = new StringBuilder();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(text);
        int lastEnd = 0;
        while (m.find()) {
            sb.append(text.substring(lastEnd, m.start()));
            String num = m.group();
            if (formatType == 1) { // Digit by Digit
                for (int i = 0; i < num.length(); i++) sb.append(num.charAt(i)).append(" ");
            } else if (formatType == 2) { // Pairs (दो-दो)
                for (int i = 0; i < num.length(); i += 2) {
                    sb.append(num.substring(i, Math.min(i + 2, num.length()))).append(" ");
                }
            } else if (formatType == 3) { // Triplets (तीन-तीन)
                for (int i = 0; i < num.length(); i += 3) {
                    sb.append(num.substring(i, Math.min(i + 3, num.length()))).append(" ");
                }
            }
            lastEnd = m.end();
        }
        sb.append(text.substring(lastEnd));
        return sb.toString();
    }

    private List<String> splitTextLossless(String text) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int lastScript = 0;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            int script = 0;
            if (ch >= '\u0900' && ch <= '\u097F') {
                script = 1;
            } else if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) {
                script = 2;
            } else if (ch >= '0' && ch <= '9') {
                script = 3; // Number script
            }

            if (script != 0 && lastScript != 0 && script != lastScript && current.length() > 0) {
                result.add(current.toString());
                current.setLength(0);
            }
            current.append(ch);
            if (script != 0) lastScript = script;

            if ((ch == '.' || ch == '!' || ch == '?' || ch == '\n' || ch == '।') && current.length() >= 80) {
                if (i + 1 < text.length() && Character.isWhitespace(text.charAt(i + 1))) {
                    result.add(current.toString());
                    current.setLength(0);
                    lastScript = 0;
                }
            }
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    private String applyPunctuationRules(String text, int level, boolean isHindi) {
        String trimmed = text.trim();
        if (trimmed.length() == 1) {
            String single = mapSymbol(trimmed.charAt(0), isHindi);
            if (single != null) return single;
            return trimmed;
        }

        if (level == 3) {
            return text.replaceAll("[@#$%&*+=~^<>_\\\\/|]+", " ");
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean nextIsDevanagari = isHindi;
            for (int j = i - 1; j >= Math.max(0, i - 4); j--) {
                char prev = text.charAt(j);
                if (prev >= '\u0900' && prev <= '\u097F') { nextIsDevanagari = true; break; }
                if ((prev >= 'a' && prev <= 'z') || (prev >= 'A' && prev <= 'Z')) { nextIsDevanagari = false; break; }
            }
            String mapped = null;
            if (isSomeSymbol(c)) {
                mapped = mapSymbol(c, nextIsDevanagari);
            } else if (level >= 1 && isMostSymbol(c)) {
                mapped = mapSymbol(c, nextIsDevanagari);
            } else if (level == 2 && isAllSymbol(c)) {
                mapped = mapSymbol(c, nextIsDevanagari);
            }
            if (mapped != null) {
                sb.append(" ").append(mapped).append(" ");
            } else {
                sb.append(c);
            }
        }
        return sb.toString().replaceAll("[ \\t]{2,}", " ");
    }

    private boolean isSomeSymbol(char c) { return "@#$%&*+=".indexOf(c) >= 0; }
    private boolean isMostSymbol(char c) { return "?!~^<>_\\/-|".indexOf(c) >= 0; }
    private boolean isAllSymbol(char c) { return ".,:;()[]{}\"'।".indexOf(c) >= 0; }

    private String mapSymbol(char c, boolean hi) {
        switch (c) {
            case '@': return "at";
            case '#': return "hash";
            case '$': return "dollar";
            case '%': return "percent";
            case '&': return hi ? "और" : "and";
            case '*': return "star";
            case '+': return "plus";
            case '=': return "equals";
            case '?': return hi ? "प्रश्नवाचक चिन्ह" : "question mark";
            case '!': return hi ? "विस्मयादिबोधक चिन्ह" : "exclamation mark";
            case '/': return "slash";
            case '\\': return "backslash";
            case '~': return "tilde";
            case '^': return "caret";
            case '<': return "less than";
            case '>': return "greater than";
            case '_': return "underscore";
            case '-': return "dash";
            case '|': return "vertical bar";
            case '.': return hi ? "बिंदु" : "dot";
            case ',': return hi ? "अल्पविराम" : "comma";
            case ':': return "colon";
            case ';': return "semicolon";
            case '(': return "open bracket";
            case ')': return "close bracket";
            case '[': return "open square bracket";
            case ']': return "close square bracket";
            case '{': return "open brace";
            case '}': return "close brace";
            case '"': return "quote";
            case '\'': return "apostrophe";
            case '।': return "पूर्ण विराम";
            default: return null;
        }
    }

    private void routeAndSynthesize(String chunk, float baseRate, float basePitch, String langCode, SynthesisCallback callback, StreamState state) {
        if (isStopped) return;
        int mode = prefs.getInt("tts_mode", 0);

        if (mode != 0) {
            JSONObject langConfig = findLanguageConfig(langCode);
            if (langConfig != null) {
                String engPkg = langConfig.optString("engine_pkg", "");
                if (!engPkg.isEmpty() && !"disabled".equals(engPkg) && !engPkg.equals(getPackageName())) {
                    float lRate = prefs.getBoolean("force_rate", false) ? (float) langConfig.optDouble("rate", baseRate) : baseRate;
                    float lPitch = prefs.getBoolean("force_pitch", false) ? (float) langConfig.optDouble("pitch", basePitch) : basePitch;

                    TextToSpeech targetTts = getOrCreateEngine(engPkg);
                    if (targetTts != null) {
                        String variant = langConfig.optString("voice_variant", "Default");
                        synthesizeChunkToCallback(targetTts, engPkg, chunk, lRate, lPitch, langCode, variant, callback, state);
                        return;
                    }
                }
            }

            if (langCode.startsWith("hi") && secondaryTts != null && !currentSecondaryEngine.isEmpty()) {
                synthesizeChunkToCallback(secondaryTts, currentSecondaryEngine, chunk, baseRate, basePitch, langCode, "Default", callback, state);
                return;
            }
        }

        synthesizeChunkToCallback(primaryTts, currentPrimaryEngine, chunk, baseRate, basePitch, langCode, "Default", callback, state);
    }

    private void synthesizeChunkToCallback(TextToSpeech tts, String enginePkg, String text, float rate, float pitch, String langCode, String variant, SynthesisCallback callback, StreamState state) {
        if (tts == null || isStopped) return;
        try {
            CountDownLatch initLatch = engineInitLatches.get(enginePkg);
            if (initLatch != null) {
                try { initLatch.await(2000, TimeUnit.MILLISECONDS); } catch (Exception ignored) {}
            }
            if (isStopped) return;

            applyVoiceAndLocale(tts, langCode, variant);
            tts.setSpeechRate(rate);
            tts.setPitch(pitch);
            enforceAccessibilityStream(tts);

            String utteranceId = "SP_" + System.nanoTime();
            File tempWav = new File(getCacheDir(), utteranceId + ".wav");
            CountDownLatch synthLatch = new CountDownLatch(1);
            activeSynthLatch = synthLatch;

            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String s) {}
                @Override public void onDone(String s) { if (utteranceId.equals(s)) synthLatch.countDown(); }
                @Override public void onError(String s) { if (utteranceId.equals(s)) synthLatch.countDown(); }
                @Override public void onError(String s, int errorCode) { if (utteranceId.equals(s)) synthLatch.countDown(); }
            });

            Bundle params = new Bundle();
            float vol = prefs.getInt("voice_tab_volume", 100) / 100.0f;
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, vol);
            params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ACCESSIBILITY);

            int res = tts.synthesizeToFile(text, params, tempWav, utteranceId);
            if (res == TextToSpeech.SUCCESS) {
                try { synthLatch.await(8, TimeUnit.SECONDS); } catch (Exception ignored) {}
            }

            if (tempWav.exists() && tempWav.length() > 44 && !isStopped) {
                streamWavFileToCallback(tempWav, callback, state, text.length() <= 6);
                try { tempWav.delete(); } catch (Exception ignored) {}
                return;
            }
            if (tempWav.exists()) {
                try { tempWav.delete(); } catch (Exception ignored) {}
            }

            if (!isStopped) {
                CountDownLatch speakLatch = new CountDownLatch(1);
                activeSynthLatch = speakLatch;
                String fallbackId = "SPF_" + System.nanoTime();
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String s) {}
                    @Override public void onDone(String s) { if (fallbackId.equals(s)) speakLatch.countDown(); }
                    @Override public void onError(String s) { if (fallbackId.equals(s)) speakLatch.countDown(); }
                });
                int sRes = tts.speak(text, TextToSpeech.QUEUE_ADD, params, fallbackId);
                if (sRes == TextToSpeech.SUCCESS) {
                    try { speakLatch.await(6, TimeUnit.SECONDS); } catch (Exception ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }

    private void streamWavFileToCallback(File wavFile, SynthesisCallback callback, StreamState state, boolean isVeryShortText) {
        try {
            byte[] fileBytes = new byte[(int) wavFile.length()];
            FileInputStream fis = new FileInputStream(wavFile);
            int totalRead = 0;
            while (totalRead < fileBytes.length) {
                int r = fis.read(fileBytes, totalRead, fileBytes.length - totalRead);
                if (r <= 0) break;
                totalRead += r;
            }
            fis.close();

            int channels = 1;
            int sampleRate = 24000;
            int dataOffset = 44;
            int dataLength = totalRead - 44;

            for (int i = 12; i < Math.min(totalRead - 8, 200); i++) {
                if (fileBytes[i] == 'f' && fileBytes[i+1] == 'm' && fileBytes[i+2] == 't' && fileBytes[i+3] == ' ') {
                    if (i + 16 <= totalRead) {
                        channels = (fileBytes[i+10] & 0xFF) | ((fileBytes[i+11] & 0xFF) << 8);
                        sampleRate = (fileBytes[i+12] & 0xFF) |
                                     ((fileBytes[i+13] & 0xFF) << 8) |
                                     ((fileBytes[i+14] & 0xFF) << 16) |
                                     ((fileBytes[i+15] & 0xFF) << 24);
                    }
                } else if (fileBytes[i] == 'd' && fileBytes[i+1] == 'a' && fileBytes[i+2] == 't' && fileBytes[i+3] == 'a') {
                    dataLength = (fileBytes[i+4] & 0xFF) |
                                 ((fileBytes[i+5] & 0xFF) << 8) |
                                 ((fileBytes[i+6] & 0xFF) << 16) |
                                 ((fileBytes[i+7] & 0xFF) << 24);
                    dataOffset = i + 8;
                    break;
                }
            }

            if (sampleRate < 8000 || sampleRate > 48000) sampleRate = 24000;
            if (channels < 1 || channels > 2) channels = 1;
            if (dataOffset >= totalRead) return;
            int availablePcmLen = Math.min(dataLength, totalRead - dataOffset);
            if (availablePcmLen <= 0) return;

            byte[] pcmData = new byte[availablePcmLen];
            System.arraycopy(fileBytes, dataOffset, pcmData, 0, availablePcmLen);

            if (channels == 2) {
                pcmData = stereoToMono16Bit(pcmData);
            }

            if (!state.started) {
                state.sampleRate = sampleRate;
                callback.start(state.sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1);
                state.started = true;
            } else if (sampleRate != state.sampleRate) {
                pcmData = resample16BitMono(pcmData, sampleRate, state.sampleRate);
            }

            if (isVeryShortText && !prefs.getBoolean("strip_attributes", false)) {
                int warmupBytes = (state.sampleRate / 25) * 2;
                if (warmupBytes % 2 != 0) warmupBytes++;
                byte[] warmup = new byte[warmupBytes];
                callback.audioAvailable(warmup, 0, warmup.length);
            }

            int maxBuf = Math.max(1024, Math.min(4096, callback.getMaxBufferSize()));
            if (maxBuf % 2 != 0) maxBuf--;
            int offset = 0;
            while (offset < pcmData.length && !isStopped) {
                int chunkLen = Math.min(maxBuf, pcmData.length - offset);
                callback.audioAvailable(pcmData, offset, chunkLen);
                offset += chunkLen;
            }
        } catch (Exception ignored) {}
    }

    private byte[] stereoToMono16Bit(byte[] stereo) {
        int frames = stereo.length / 4;
        byte[] mono = new byte[frames * 2];
        for (int i = 0; i < frames; i++) {
            short left = (short) ((stereo[i * 4] & 0xFF) | (stereo[i * 4 + 1] << 8));
            short right = (short) ((stereo[i * 4 + 2] & 0xFF) | (stereo[i * 4 + 3] << 8));
            short avg = (short) ((left + right) / 2);
            mono[i * 2] = (byte) (avg & 0xFF);
            mono[i * 2 + 1] = (byte) ((avg >> 8) & 0xFF);
        }
        return mono;
    }

    private byte[] resample16BitMono(byte[] input, int srcRate, int dstRate) {
        int srcSamples = input.length / 2;
        if (srcSamples <= 1) return input;
        int dstSamples = (int) (((long) srcSamples * dstRate) / srcRate);
        byte[] output = new byte[dstSamples * 2];
        for (int i = 0; i < dstSamples; i++) {
            double srcIdx = ((double) i * srcRate) / dstRate;
            int idx0 = (int) srcIdx;
            int idx1 = Math.min(idx0 + 1, srcSamples - 1);
            double frac = srcIdx - idx0;
            short s0 = (short) ((input[idx0 * 2] & 0xFF) | (input[idx0 * 2 + 1] << 8));
            short s1 = (short) ((input[idx1 * 2] & 0xFF) | (input[idx1 * 2 + 1] << 8));
            short sample = (short) (s0 + frac * (s1 - s0));
            output[i * 2] = (byte) (sample & 0xFF);
            output[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return output;
    }

    private synchronized TextToSpeech getOrCreateEngine(String pkg) {
        if (pkg == null || pkg.isEmpty() || "disabled".equals(pkg) || pkg.equals(getPackageName())) return null;
        if (pkg.equals(currentPrimaryEngine)) return primaryTts;
        if (pkg.equals(currentSecondaryEngine)) return secondaryTts;
        if (extraEngines.containsKey(pkg)) return extraEngines.get(pkg);

        final CountDownLatch latch = new CountDownLatch(1);
        engineInitLatches.put(pkg, latch);
        TextToSpeech engine = new TextToSpeech(this, status -> {
            enforceAccessibilityStream(extraEngines.get(pkg));
            latch.countDown();
        }, pkg);
        extraEngines.put(pkg, engine);
        return engine;
    }

    private JSONObject findLanguageConfig(String reqLang) {
        if (prefs == null) return null;
        Set<String> keys = prefs.getStringSet("configured_lang_keys", null);
        if (keys == null || keys.isEmpty()) return null;
        String lp = (reqLang != null) ? reqLang.toLowerCase(Locale.US).trim() : "";
        String lp2 = lp.length() >= 2 ? lp.substring(0, 2) : lp;
        for (String k : keys) {
            String code = k.replace("lang_map_", "").toLowerCase(Locale.US).trim();
            String code2 = code.length() >= 2 ? code.substring(0, 2) : code;
            if (code.equals(lp) || code2.equals(lp2) || code.startsWith(lp) || lp.startsWith(code)) {
                try { return new JSONObject(prefs.getString(k, "")); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private void applyVoiceAndLocale(TextToSpeech tts, String langCode, String variant) {
        if (tts == null) return;
        String norm = (langCode != null && !langCode.isEmpty()) ? langCode.toLowerCase(Locale.US).trim() : "en";
        Locale targetLocale;
        if (norm.startsWith("hi")) {
            targetLocale = new Locale("hi", "IN");
        } else if (norm.startsWith("en")) {
            targetLocale = new Locale("en", "IN");
        } else {
            targetLocale = Locale.forLanguageTag(norm);
        }

        try {
            int r = tts.setLanguage(targetLocale);
            if (r < TextToSpeech.LANG_AVAILABLE && norm.startsWith("en")) {
                targetLocale = Locale.US;
                tts.setLanguage(targetLocale);
            }
        } catch (Exception ignored) {}

        try {
            Set<Voice> voices = tts.getVoices();
            if (voices != null && !voices.isEmpty()) {
                if (variant != null && !variant.isEmpty() && !variant.equals("Default") && !variant.equals("Default Voice")) {
                    for (Voice v : voices) {
                        if (v.getName().equals(variant)) {
                            tts.setVoice(v);
                            return;
                        }
                    }
                }
                String targetPrefix = norm.length() >= 2 ? norm.substring(0, 2) : norm;
                Voice currentVoice = tts.getVoice();
                boolean wrongVoice = (currentVoice == null || currentVoice.getLocale() == null);
                if (!wrongVoice) {
                    String curLang = currentVoice.getLocale().getLanguage().toLowerCase(Locale.US);
                    if (!curLang.startsWith(targetPrefix)) {
                        wrongVoice = true;
                    }
                }
                if (wrongVoice) {
                    Voice fallbackVoice = null;
                    for (Voice v : voices) {
                        if (v.getLocale() != null) {
                            String vLang = v.getLocale().getLanguage().toLowerCase(Locale.US);
                            if (vLang.startsWith(targetPrefix)) {
                                if (!v.isNetworkConnectionRequired()) {
                                    fallbackVoice = v;
                                    break;
                                } else if (fallbackVoice == null) {
                                    fallbackVoice = v;
                                }
                            }
                        }
                    }
                    if (fallbackVoice != null) {
                        tts.setVoice(fallbackVoice);
                    }
                }
            }
        } catch (Exception ignored) {}
    }
}
