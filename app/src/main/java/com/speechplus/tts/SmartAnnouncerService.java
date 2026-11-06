package com.speechplus.tts;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import java.util.Calendar;
import java.util.Locale;

public class SmartAnnouncerService extends Service implements TextToSpeech.OnInitListener {
    private TextToSpeech tts;
    private AudioManager audioManager;
    private SharedPreferences prefs;
    private boolean isTtsReady = false;
    private BroadcastReceiver batteryReceiver;
    private BroadcastReceiver timeReceiver;
    private TelephonyManager telephonyManager;
    private PhoneStateListener phoneStateListener;
    private int lastBatteryLevel = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("SpeechPlusPrefs", MODE_PRIVATE);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        tts = new TextToSpeech(this, this);
        registerReceivers();
        try { setupCallListener(); } catch (java.lang.SecurityException e) { e.printStackTrace(); } catch (Exception e) {}
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.US);
            isTtsReady = true;
        }
    }

    private boolean shouldSpeak() {
        boolean respectSilent = prefs.getBoolean("silent_mode", false);
        if (!respectSilent) return true;
        return audioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL;
    }

    private void playAudioFile(int resId) {
        if (!shouldSpeak()) return;
        try {
            MediaPlayer mediaPlayer = MediaPlayer.create(this, resId);
            if (mediaPlayer != null) {
                mediaPlayer.setOnCompletionListener(mp -> mp.release());
                mediaPlayer.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void playChimeAndSpeak(String text) {
        if (!isTtsReady || !shouldSpeak()) return;
        try {
            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            MediaPlayer mp = MediaPlayer.create(this, soundUri);
            if (mp != null) {
                mp.setOnCompletionListener(mediaPlayer -> {
                    mediaPlayer.release();
                    tts.speak(text, TextToSpeech.QUEUE_ADD, null, null);
                });
                mp.start();
            } else {
                tts.speak(text, TextToSpeech.QUEUE_ADD, null, null);
            }
        } catch (Exception e) {
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, null);
        }
    }

    private void registerReceivers() {
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_POWER_CONNECTED.equals(action) && prefs.getBoolean("announce_charger", false)) {
                    playAudioFile(R.raw.charger_plugged);
                } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action) && prefs.getBoolean("announce_charger", false)) {
                    playAudioFile(R.raw.charger_unplugged);
                } else if (Intent.ACTION_BATTERY_CHANGED.equals(action) && prefs.getBoolean("announce_battery", false)) {
                    int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    if (level == 20 && lastBatteryLevel != 20 && prefs.getBoolean("announce_battery_alerts", false)) {
                        playAudioFile(R.raw.battery_low);
                    }
                    lastBatteryLevel = level;
                }
            }
        };
        IntentFilter batteryFilter = new IntentFilter();
        batteryFilter.addAction(Intent.ACTION_POWER_CONNECTED);
        batteryFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        batteryFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryReceiver, batteryFilter);
        timeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_TIME_TICK.equals(intent.getAction()) && prefs.getBoolean("announce_time", false)) {
                    Calendar c = Calendar.getInstance();
                    int minute = c.get(Calendar.MINUTE);
                    int interval = Integer.parseInt(prefs.getString("time_interval", "60"));
                    if (minute % interval == 0) {
                        int hour = c.get(Calendar.HOUR);
                        if (hour == 0) hour = 12;
                        String amPm = c.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM";
                        String timeText = "The time is " + hour + " " + (minute == 0 ? "o'clock" : minute) + " " + amPm;
                        // टाइम के लिए प्यारा साउंड बजेगा
                        playChimeAndSpeak(timeText);
                    }
                }
            }
        };
        IntentFilter timeFilter = new IntentFilter(Intent.ACTION_TIME_TICK);
        registerReceiver(timeReceiver, timeFilter);
    }

    private void setupCallListener() {
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                if (state == TelephonyManager.CALL_STATE_RINGING && prefs.getBoolean("announce_call", false)) {
                    String caller = (incomingNumber != null && !incomingNumber.isEmpty()) ? incomingNumber : "Unknown number";
                    int formatIdx = prefs.getInt("call_format", 1);
                    String textToSpeak = formatIdx == 1 ? "Incoming call from " + caller : caller;
                    
                    // रिंगटोन के साथ कोई एक्स्ट्रा साउंड नहीं, सीधा TTS बोलेगा
                    if (isTtsReady && shouldSpeak()) {
                        tts.speak(textToSpeak, TextToSpeech.QUEUE_ADD, null, null);
                    }
                }
            }
        };
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    @Override
    public void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (batteryReceiver != null) unregisterReceiver(batteryReceiver);
        if (timeReceiver != null) unregisterReceiver(timeReceiver);
        if (telephonyManager != null && phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
