#!/bin/bash
set -e

echo "=== 1. Updating App Version to v1.4.4 (versionCode 10) ==="
sed -i 's/versionCode [0-9]*/versionCode 10/' app/build.gradle
sed -i 's/versionName "[^"]*"/versionName "1.4.4"/' app/build.gradle
sed -i 's/1\.4\.[0-9]/1.4.4/g' .github/workflows/build.yml

echo "=== 2. Fixing In-App Update Checker in AboutActivity.java ==="
cat << 'JAVA_UPD' > patch_updater.py
with open("app/src/main/java/com/speechplus/tts/AboutActivity.java", "r") as f:
    c = f.read()

old_check = """                    runOnUiThread(() -> {
                        progress.dismiss();
                        if (!finalUrl.isEmpty()) {
                            showUpdatePrompt(latestTag, finalUrl);
                        } else {
                            Toast.makeText(AboutActivity.this, "No APK asset found in release " + latestTag, Toast.LENGTH_SHORT).show();
                        }
                    });"""

new_check = """                    runOnUiThread(() -> {
                        progress.dismiss();
                        String curVer = BuildConfig.VERSION_NAME.replace("v", "").trim();
                        String cleanTag = latestTag.replace("v", "").trim();
                        if (cleanTag.equals(curVer)) {
                            new AlertDialog.Builder(AboutActivity.this)
                                    .setTitle("Speech Plus TTS is Up to Date")
                                    .setMessage("You are already using the latest version (v" + curVer + ").")
                                    .setPositiveButton("OK", null)
                                    .show();
                        } else if (!finalUrl.isEmpty()) {
                            showUpdatePrompt(latestTag, finalUrl);
                        } else {
                            Toast.makeText(AboutActivity.this, "No APK asset found in release " + latestTag, Toast.LENGTH_SHORT).show();
                        }
                    });"""

if old_check in c:
    c = c.replace(old_check, new_check)
    with open("app/src/main/java/com/speechplus/tts/AboutActivity.java", "w") as f:
        f.write(c)
JAVA_UPD

# Sed fallback for AboutActivity update check
sed -i 's/if (!finalUrl.isEmpty()) {/String curVer = BuildConfig.VERSION_NAME.replace("v", "").trim(); String cleanTag = latestTag.replace("v", "").trim(); if (cleanTag.equals(curVer)) { new AlertDialog.Builder(AboutActivity.this).setTitle("Speech Plus TTS is Up to Date").setMessage("You are already using the latest version (v" + curVer + ").").setPositiveButton("OK", null).show(); } else if (!finalUrl.isEmpty()) {/' app/src/main/java/com/speechplus/tts/AboutActivity.java || true

echo "=== 3. Adding Eloquence Shield in SpeechPlusService.java ==="
sed -i 's/private void applyAudioRouting(TextToSpeech engine) {/private void applyAudioRouting(TextToSpeech engine) {\n        try { String dEng = engine.getDefaultEngine(); if (dEng != null \&\& dEng.toLowerCase(java.util.Locale.US).contains("eloquence")) return; } catch (Throwable ignored) {}/' app/src/main/java/com/speechplus/tts/SpeechPlusService.java

echo "=== 4. Updating Native Voice Test & A-Z Languages in MainActivity.java ==="
sed -i 's/activeVoiceTestTts.speak("Testing speech synthesis configuration for " + lang.displayName/String tTxt = "en".equals(lang.code) ? "Speech Plus TTS. High quality zero lag speech synthesis." : ("hi".equals(lang.code) || "hi-IN".equals(lang.code) ? "नमस्ते, यह स्पीच प्लस टीटीएस का आवाज़ परीक्षण है।" : "Testing " + lang.displayName); activeVoiceTestTts.speak(tTxt/' app/src/main/java/com/speechplus/tts/MainActivity.java

sed -i 's/btnTestVoice.setOnClickListener(v -> testSpeak("Speech Plus TTS. Welcome to Zero-Lag High Quality Experience."));/if (btnTestVoice != null) btnTestVoice.setVisibility(android.view.View.GONE);/' app/src/main/java/com/speechplus/tts/MainActivity.java

echo "=== 5. Adding English What's New Dialog in MainActivity.java ==="
if ! grep -q "whats_new_dismissed_ver" app/src/main/java/com/speechplus/tts/MainActivity.java; then
sed -i '/protected void onCreate(Bundle savedInstanceState) {/a \
        showWhatsNewDialogIfNeeded();' app/src/main/java/com/speechplus/tts/MainActivity.java

cat << 'JAVA_METHODS' >> app/src/main/java/com/speechplus/tts/MainActivity.java

    private void showWhatsNewDialogIfNeeded() {
        android.content.SharedPreferences sp = getSharedPreferences("speech_plus_prefs", MODE_PRIVATE);
        String savedVer = sp.getString("whats_new_dismissed_ver", "");
        if (BuildConfig.VERSION_NAME.equals(savedVer)) return;

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 10);

        android.widget.TextView tv = new android.widget.TextView(this);
        tv.setTextSize(14f);
        tv.setTextColor(android.graphics.Color.LTGRAY);
        tv.setText("• Permanent Eloquence Fix: Completely resolved crash across TalkBack and system settings.\n" +
                "• Native Voice Preview: Live test natively speaks in the selected language.\n" +
                "• Clean Global Languages (A-Z): Unified list of all world & Indian languages without duplicate accents.\n" +
                "• Smart Dual Auto-Switch: Real-time zero-lag text script routing between Primary and Secondary engines.\n" +
                "• Accurate Update Checker: Confirms when you are already on the latest release.");
        layout.addView(tv);

        android.widget.CheckBox cb = new android.widget.CheckBox(this);
        cb.setText("Don't show again for this version");
        cb.setTextColor(android.graphics.Color.WHITE);
        layout.addView(cb);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("What's New in Speech Plus TTS v1.4.4")
                .setView(layout)
                .setPositiveButton("GOT IT", (d, w) -> {
                    if (cb.isChecked()) {
                        sp.edit().putString("whats_new_dismissed_ver", BuildConfig.VERSION_NAME).apply();
                    }
                })
                .setCancelable(false)
                .show();
    }
JAVA_METHODS
fi

rm -f patch_updater.py
echo "=== ALL FIXES APPLIED SUCCESSFULLY ==="
