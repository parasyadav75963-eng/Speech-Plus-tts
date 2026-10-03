package com.speechplus.tts;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import org.json.JSONArray;
import org.json.JSONObject;

public class AboutActivity extends Activity {

    private void openWhatsApp(String phone, String msg) {
        try {
            String url = "https://api.whatsapp.com/send?phone=" + phone + "&text=" + URLEncoder.encode(msg, "UTF-8");
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "WhatsApp error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private Button makeButton(String text, LinearLayout.LayoutParams lp) {
        Button b = new Button(this);
        b.setText(text);
        b.setLayoutParams(lp);
        return b;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Interested Technology for Blind");
        tvTitle.setTextSize(22);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setPadding(0, 0, 0, 8);
        layout.addView(tvTitle);

        TextView tvSubTitle = new TextView(this);
        tvSubTitle.setText("Speech Plus TTS - Official Community & Support");
        tvSubTitle.setTextSize(14);
        tvSubTitle.setTextColor(0xFF00E676);
        tvSubTitle.setPadding(0, 0, 0, 16);
        layout.addView(tvSubTitle);

        TextView tvDesc = new TextView(this);
        String desc = "Welcome to Interested Technology for Blind Community – The Real Game-Changer for Visually Impaired Users!\n\n"
                + "We don't just talk tech – we make tech *feelable*. From screen reader mastery to groundbreaking accessible tools like Speech Plus TTS, from accessible gaming to smart Android hacks – this is the ultimate empowerment zone for blind users who want to explore their technology beyond limits.\n\n"
                + "Our community is not just a group – it's confidence, clarity, and independence rolled into one. Whether you're a beginner or a tech ninja with no sight, here you'll find genuine guidance, voice-first apps, peer support, and innovations that truly speak your language – audio!\n\n"
                + "⚡ Real tricks. 💡 Real empowerment. 🎧 100% Voice-Accessible. No confusion. No complications. Just pure usable tech.\n\n"
                + "Join our growing community – because *here, blindness meets brilliance!*";
        tvDesc.setText(desc);
        tvDesc.setTextSize(15);
        tvDesc.setTextColor(0xFFE0E0E0);
        tvDesc.setLineSpacing(6f, 1f);
        tvDesc.setPadding(0, 0, 0, 24);
        layout.addView(tvDesc);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 16);

        // 1. Check for Updates
        Button btnCheckUpdate = makeButton("Check for Updates", lp);
        btnCheckUpdate.setOnClickListener(v -> checkForUpdatesDirect());
        layout.addView(btnCheckUpdate);

        // 2. YouTube Channel & Tutorials
        Button btnYt = makeButton("Visit YouTube Channel & Tutorials", lp);
        btnYt.setOnClickListener(v -> openUrl("https://www.youtube.com/@InterestedTechnologyforBlind"));
        layout.addView(btnYt);

        // 3. Web Portal
        Button btnPortal = makeButton("Join our groups (Web Portal)", lp);
        btnPortal.setOnClickListener(v -> openUrl("https://react-9bhsjt.onspace.build"));
        layout.addView(btnPortal);

        // 4. Helpline 1 Join Request
        Button btnWa1 = makeButton("Join Request (WhatsApp +916260503286)", lp);
        btnWa1.setOnClickListener(v -> openWhatsApp("916260503286", "Hello, I want to join Interested Technology for Blind group"));
        layout.addView(btnWa1);

        // 5. Helpline 2 Join Request
        Button btnWa2 = makeButton("Join Request (WhatsApp +919303472553)", lp);
        btnWa2.setOnClickListener(v -> openWhatsApp("919303472553", "Hello, I want to join Interested Technology for Blind group"));
        layout.addView(btnWa2);

        // 6. WhatsApp Channel
        Button btnWaChannel = makeButton("Follow WhatsApp Channel", lp);
        btnWaChannel.setOnClickListener(v -> openUrl("https://whatsapp.com/channel/0029Va4fpB4GJP8PZEIV5m2e"));
        layout.addView(btnWaChannel);

        // 7. Telegram Channel
        Button btnTgChannel = makeButton("Join Telegram Channel", lp);
        btnTgChannel.setOnClickListener(v -> openUrl("https://t.me/interestedtechnologyforblind"));
        layout.addView(btnTgChannel);

        // 8. Facebook Page
        Button btnFb = makeButton("Follow on Facebook", lp);
        btnFb.setOnClickListener(v -> openUrl("https://facebook.com/Interestedtechnologyforblind"));
        layout.addView(btnFb);

        // 9. GitHub Repository
        Button btnGithub = makeButton("View GitHub Repository", lp);
        btnGithub.setOnClickListener(v -> openUrl("https://github.com/parasyadav75963-eng/Speech-Plus-tts"));
        layout.addView(btnGithub);

        // 10. Email Support
        Button btnEmail = makeButton("Contact via Email (contact.i.t.f.b@gmail.com)", lp);
        btnEmail.setOnClickListener(v -> {
            try {
                Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
                emailIntent.setData(Uri.parse("mailto:contact.i.t.f.b@gmail.com"));
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Speech Plus TTS - Community Inquiry");
                startActivity(Intent.createChooser(emailIntent, "Send Email"));
            } catch (Exception e) {
                Toast.makeText(this, "Email client not available", Toast.LENGTH_SHORT).show();
            }
        });
        layout.addView(btnEmail);

        scrollView.addView(layout);
        setContentView(scrollView);
    }

    private void checkForUpdatesDirect() {
        ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage("Checking for updates...");
        progress.setCancelable(false);
        progress.show();

        new Thread(() -> {
            try {
                URL url = new URL("https://api.github.com/repos/parasyadav75963-eng/Speech-Plus-tts/releases/latest");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "SpeechPlusTTS-Updater");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                if (conn.getResponseCode() == 200) {
                    InputStream in = conn.getInputStream();
                    java.util.Scanner s = new java.util.Scanner(in).useDelimiter("\\A");
                    String resp = s.hasNext() ? s.next() : "";
                    JSONObject json = new JSONObject(resp);
                    String latestTag = json.optString("tag_name", "");

                    JSONArray assets = json.optJSONArray("assets");
                    String downloadUrl = "";
                    if (assets != null) {
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject asset = assets.getJSONObject(i);
                            String name = asset.optString("name", "");
                            if (name.endsWith(".apk")) {
                                downloadUrl = asset.optString("browser_download_url", "");
                                break;
                            }
                        }
                    }

                    final String finalUrl = downloadUrl;
                    runOnUiThread(() -> {
                        progress.dismiss();
                        String curVer = "1.4.4"; try { curVer = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) {} curVer = curVer.replace("v", "").trim(); String cleanTag = latestTag.replace("v", "").trim(); if (cleanTag.equals(curVer)) { new AlertDialog.Builder(AboutActivity.this).setTitle("Speech Plus TTS is Up to Date").setMessage("You are already using the latest version (v" + curVer + ").").setPositiveButton("OK", null).show(); } else if (!finalUrl.isEmpty()) {
                            showUpdatePrompt(latestTag, finalUrl);
                        } else {
                            Toast.makeText(AboutActivity.this, "No APK asset found in release " + latestTag, Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    int code = conn.getResponseCode();
                    runOnUiThread(() -> {
                        progress.dismiss();
                        Toast.makeText(AboutActivity.this, "Update check failed: Server code " + code, Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.dismiss();
                    Toast.makeText(AboutActivity.this, "Network error checking update: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void showUpdatePrompt(String tag, String dlUrl) {
        new AlertDialog.Builder(this)
                .setTitle("Update Available (" + tag + ")")
                .setMessage("A new official release is available. Download and install now?")
                .setPositiveButton("Update Now", (d, w) -> downloadAndInstall(dlUrl))
                .setNegativeButton("Later", null)
                .show();
    }

    private void downloadAndInstall(String dlUrl) {
        ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage("Downloading update...");
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setIndeterminate(false);
        progress.setMax(100);
        progress.setCancelable(false);
        progress.show();

        new Thread(() -> {
            try {
                URL url = new URL(dlUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                int fileLength = conn.getContentLength();

                File file = new File(getExternalFilesDir(null), "update.apk");
                InputStream in = conn.getInputStream();
                FileOutputStream out = new FileOutputStream(file);

                byte[] buffer = new byte[8192];
                long total = 0;
                int count;
                while ((count = in.read(buffer)) != -1) {
                    total += count;
                    if (fileLength > 0) {
                        int progressPercent = (int) (total * 100 / fileLength);
                        runOnUiThread(() -> progress.setProgress(progressPercent));
                    }
                    out.write(buffer, 0, count);
                }
                out.flush();
                out.close();
                in.close();

                runOnUiThread(() -> {
                    progress.dismiss();
                    installApk(file);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.dismiss();
                    Toast.makeText(AboutActivity.this, "Download failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void installApk(File file) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri apkUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                apkUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                apkUri = Uri.fromFile(file);
            }
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Cannot install APK: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
