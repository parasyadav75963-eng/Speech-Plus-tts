use strict;
use warnings;

my $xml_path = "app/src/main/res/layout/activity_main.xml";
open(my $fh, "<", $xml_path) or die "Cannot open XML";
local $/ = undef;
my $xml_content = <$fh>;
close($fh);

my $home_features = <<'HOME_UI';
                <!-- SMART ANNOUNCER SETTINGS -->
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Smart Announcers (Beta)"
                    android:textColor="#00E676"
                    android:textSize="18sp"
                    android:textStyle="bold"
                    android:layout_marginTop="16dp"
                    android:layout_marginBottom="8dp" />

                <!-- GLOBAL SILENT MODE TOGGLE -->
                <Switch
                    android:id="@+id/switchSilentMode"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="Respect Silent Mode"
                    android:textColor="#FF9800"
                    android:textSize="16sp"
                    android:padding="8dp"
                    android:contentDescription="Toggle to mute Battery and Time announcers when phone is on silent or vibrate." />

                <View android:layout_width="match_parent" android:layout_height="1dp" android:background="#424242" android:layout_marginVertical="8dp"/>

                <!-- 1. Battery Announcer -->
                <Switch
                    android:id="@+id/switchBattery"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="Battery Announcer"
                    android:textColor="#FFFFFF"
                    android:textSize="16sp"
                    android:padding="8dp"
                    android:contentDescription="Toggle to announce battery level." />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Battery Announce Interval:"
                    


cd ~/SpeechPlusTTS
cat << 'EOF' > phase2_java_logic.pl
use strict;
use warnings;

# 1. Update SmartAnnouncerService.java with new Advanced Logic (Intervals & Silent Mode)
my $service_path = "app/src/main/java/com/speechplus/tts/SmartAnnouncerService.java";
open(my $fh_srv, ">", $service_path) or die "Cannot write Service";

my $service_code = <<'SERVICE';
package com.speechplus.tts;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.speech.tts.TextToSpeech;
import android.telephony.TelephonyManager;
import java.util.Calendar;

public class SmartAnnouncerService extends Service implements TextToSpeech.OnInitListener {
    private TextToSpeech tts;
    private SharedPreferences prefs;
    private boolean isTtsReady = false;
    private int lastBatteryLevel = -1;
    private long lastTimeAnnounced = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("speechplus_prefs", MODE_PRIVATE);
        tts = new TextToSpeech(this, this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        filter.addAction(Intent.ACTION_TIME_TICK);
        registerReceiver(smartReceiver, filter);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true;
            tts.setSpeechRate(1.0f);
            tts.setPitch(1.0f);
        }
    }

    private boolean isSilentMode() {
        if (!prefs.getBoolean("respect_silent_mode", false)) return false;
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        return am != null && am.getRingerMode() != AudioManager.RINGER_MODE_NORMAL;
    }

    private final BroadcastReceiver smartReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!isTtsReady) return;
            String action = intent.getAction();

            // 1. CALL ANNOUNCER
            if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action) && prefs.getBoolean("enable_call_announcer", false)) {
                String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
                    announceCall(context, intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER));
                } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
                    tts.stop();
                }
            }

            // 2. BATTERY ANNOUNCER (Respects Silent Mode)
            if (prefs.getBoolean("enable_battery_announcer", false) && !isSilentMode()) {
                if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
                    speak("Charger connected. Battery is at " + getBatteryLevel(context) + " percent.");
                } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
                    speak("Charger disconnected.");
                } else if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                    int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    if (level != lastBatteryLevel) {
                        int intervalMode = prefs.getInt("battery_interval_mode", 0); // 0=10%, 1=15m, 2=30m, 3=1h
                        boolean shouldAnnounce = false;
                        if (intervalMode == 0 && (level % 10 == 0 || level == 100 || level <= 15)) {
                            shouldAnnounce = true;
                        }
                        if (shouldAnnounce) {
                            speak("Battery alert: Battery is at " + level + " percent.");
                            lastBatteryLevel = level;
                        }
                    }
                }
            }

            // 3. TIME ANNOUNCER (Respects Silent Mode)
            if (Intent.ACTION_TIME_TICK.equals(action) && prefs.getBoolean("enable_time_announcer", false) && !isSilentMode()) {
                Calendar cal = Calendar.getInstance();
                int min = cal.get(Calendar.MINUTE);
                int intervalMode = prefs.getInt("time_interval_mode", 3); // 0=15m, 1=30m, 2=45m, 3=1h
                
                boolean shouldAnnounce = false;
                if (intervalMode == 0 && min % 15 == 0) shouldAnnounce = true;
                else if (intervalMode == 1 && min % 30 == 0) shouldAnnounce = true;
                else if (intervalMode == 2 && min % 45 == 0) shouldAnnounce = true;
                else if (intervalMode == 3 && min == 0) shouldAnnounce = true;

                if (shouldAnnounce) {
                    long now = System.currentTimeMillis();
                    if (now - lastTimeAnnounced > 60000) { // Prevent double announce in same minute
                        announceTime(cal);
                        lastTimeAnnounced = now;
                    }
                }
            }
        }
    };

    private int getBatteryLevel(Context context) {
        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return intent != null ? intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : 0;
    }

    private void announceCall(Context context, String number) {
        if (number == null || number.isEmpty()) return;
        String callerName = getContactName(context, number);
        int formatMode = prefs.getInt("call_format_mode", 0);
        String announcement = "Incoming call.";
        if (formatMode == 0 && callerName != null) announcement = "Incoming call from " + callerName;
        else if (formatMode == 1) announcement = "Incoming call from " + number.replaceAll(".(?=.)", "$0 ");
        else if (formatMode == 2) announcement = "Incoming call from " + (callerName != null ? callerName : "Unknown") + ", number " + number.replaceAll(".(?=.)", "$0 ");

        int repeatCount = prefs.getInt("call_repeat_count", 0) + 1; // 0 index = 1 time
        for (int i = 0; i < repeatCount; i++) speak(announcement);
    }

    private String getContactName(Context context, String phoneNumber) {
        try {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
            Cursor cursor = context.getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    String name = cursor.getString(0);
                    cursor.close();
                    return name;
                }
                cursor.close();
            }
        } catch (Exception e) {}
        return null;
    }

    private void announceTime(Calendar cal) {
        int hour = cal.get(Calendar.HOUR);
        if (hour == 0) hour = 12;
        int min = cal.get(Calendar.MINUTE);
        String ampm = cal.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM";
        int timeFormat = prefs.getInt("time_format_mode", 0);
        String timeStr = (timeFormat == 0) ? "The time is exactly " + hour + " " + (min > 0 ? min : "o'clock") + " " + ampm : "It is " + hour + " " + (min > 0 ? min : "") + " " + ampm;
        speak(timeStr);
    }

    private void speak(String text) {
        if (isTtsReady && tts != null) tts.speak(text, TextToSpeech.QUEUE_ADD, null, "SmartAnnouncer");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }
    @Override
    public IBinder onBind(Intent intent) { return null; }
    @Override
    public void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        unregisterReceiver(smartReceiver);
        super.onDestroy();
    }
}
SERVICE

print $fh_srv $service_code;
close($fh_srv);

# 2. Update MainActivity.java to link UI Switches & Spinners
my $main_path = "app/src/main/java/com/speechplus/tts/MainActivity.java";
open(my $fh_main, "<", $main_path) or die "Cannot read MainActivity";
local $/ = undef;
my $main_content = <$fh_main>;
close($fh_main);

if ($main_content !~ /setupSmartAnnouncerUI/) {
    my $ui_logic = <<'UI_LOGIC';
    private void setupSmartAnnouncerUI() {
        android.content.SharedPreferences prefs = getSharedPreferences("speechplus_prefs", MODE_PRIVATE);
        
        // Find Views
        android.widget.Switch swSilent = findViewById(R.id.switchSilentMode);
        android.widget.Switch swBattery = findViewById(R.id.switchBattery);
        android.widget.Spinner spBatteryInt = findViewById(R.id.spinnerBatteryInterval);
        android.widget.Switch swTime = findViewById(R.id.switchTime);
        android.widget.Spinner spTimeFmt = findViewById(R.id.spinnerTimeFormat);
        android.widget.Spinner spTimeInt = findViewById(R.id.spinnerTimeInterval);
        android.widget.Switch swCall = findViewById(R.id.switchCall);
        android.widget.Spinner spCallFmt = findViewById(R.id.spinnerCallFormat);
        android.widget.Spinner spCallRep = findViewById(R.id.spinnerCallRepeat);

        if (swSilent == null || swBattery == null) return; // UI not loaded yet

        // Load Saved States
        swSilent.setChecked(prefs.getBoolean("respect_silent_mode", false));
        swBattery.setChecked(prefs.getBoolean("enable_battery_announcer", false));
        spBatteryInt.setSelection(prefs.getInt("battery_interval_mode", 0));
        swTime.setChecked(prefs.getBoolean("enable_time_announcer", false));
        spTimeFmt.setSelection(prefs.getInt("time_format_mode", 0));
        spTimeInt.setSelection(prefs.getInt("time_interval_mode", 3));
        swCall.setChecked(prefs.getBoolean("enable_call_announcer", false));
        spCallFmt.setSelection(prefs.getInt("call_format_mode", 0));
        spCallRep.setSelection(prefs.getInt("call_repeat_count", 0));

        // Set Listeners to Save Data instantly
        swSilent.setOnCheckedChangeListener((btn, isChecked) -> prefs.edit().putBoolean("respect_silent_mode", isChecked).apply());
        swBattery.setOnCheckedChangeListener((btn, isChecked) -> prefs.edit().putBoolean("enable_battery_announcer", isChecked).apply());
        swTime.setOnCheckedChangeListener((btn, isChecked) -> prefs.edit().putBoolean("enable_time_announcer", isChecked).apply());
        swCall.setOnCheckedChangeListener((btn, isChecked) -> prefs.edit().putBoolean("enable_call_announcer", isChecked).apply());

        android.widget.AdapterView.OnItemSelectedListener spinnerListener = new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int pos, long id) {
                if (parent == spBatteryInt) prefs.edit().putInt("battery_interval_mode", pos).apply();
                else if (parent == spTimeFmt) prefs.edit().putInt("time_format_mode", pos).apply();
                else if (parent == spTimeInt) prefs.edit().putInt("time_interval_mode", pos).apply();
                else if (parent == spCallFmt) prefs.edit().putInt("call_format_mode", pos).apply();
                else if (parent == spCallRep) prefs.edit().putInt("call_repeat_count", pos).apply();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        };

        spBatteryInt.setOnItemSelectedListener(spinnerListener);
        spTimeFmt.setOnItemSelectedListener(spinnerListener);
        spTimeInt.setOnItemSelectedListener(spinnerListener);
        spCallFmt.setOnItemSelectedListener(spinnerListener);
        spCallRep.setOnItemSelectedListener(spinnerListener);
        
        // Start Service if any announcer is enabled
        startService(new Intent(this, SmartAnnouncerService.class));
    }
UI_LOGIC

    $main_content =~ s/(\}\s*)$/\n$ui_logic\n$1/;
    
    # Call it in onCreate
    if ($main_content =~ /(setupTabs\(\);)/) {
        $main_content =~ s/(setupTabs\(\);)/$1\n        setupSmartAnnouncerUI();/;
    }
    
    open(my $out_main, ">", $main_path);
    print $out_main $main_content;
    close($out_main);
    print "\n>>> PHASE 2 (PART 4): FULLY CONNECTED UI TO BACKEND LOGIC! <<<\n";
} else {
    print "\n>>> UI LOGIC ALREADY ADDED! <<<\n";
}
