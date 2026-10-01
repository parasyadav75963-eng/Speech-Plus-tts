package com.speechplus.tts;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.net.URLEncoder;

public class AboutActivity extends Activity {

    private void openWhatsApp(String phoneNumber, String message) {
        try {
            String url = "https://api.whatsapp.com/send?phone=" + phoneNumber + "&text=" + URLEncoder.encode(message, "UTF-8");
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        tvTitle.setTextColor(0xFF03DAC5);
        tvTitle.setPadding(0, 0, 0, 16);
        layout.addView(tvTitle);

        TextView tvDesc = new TextView(this);
        tvDesc.setText("Welcome to Interested Technology for Blind - The premier assistive tech community dedicated to empowering visually impaired users worldwide.\n\n"
                + "We are committed to making technology truly accessible, intuitive, and practical. From advanced screen reader techniques and Android accessibility optimizations to smart digital utilities and interactive discussions, this community is the ultimate hub for blind users to explore technology without limits.\n\n"
                + "Real Tech Solutions. Active Community Support. 100% Voice and Screen-Reader Accessible.\n"
                + "Official Contact: contact.i.t.f.b@gmail.com\n"
                + "Join our mission - where accessibility meets innovation!");
        tvDesc.setTextSize(14);
        tvDesc.setTextColor(0xFFE0E0E0);
        tvDesc.setPadding(0, 0, 0, 24);
        layout.addView(tvDesc);

        final String joinMsg = "Hello Interested Technology for Blind Team, I would like to join your WhatsApp community group. Please add me.";

        Button btnWa1 = new Button(this);
        btnWa1.setText("Request to Join WhatsApp Group (Option 1)");
        btnWa1.setOnClickListener(v -> openWhatsApp("916260503286", joinMsg));
        layout.addView(btnWa1);

        Button btnWa2 = new Button(this);
        btnWa2.setText("Request to Join WhatsApp Group (Option 2)");
        btnWa2.setOnClickListener(v -> openWhatsApp("919303472553", joinMsg));
        layout.addView(btnWa2);

        Button btnPortal = new Button(this);
        btnPortal.setText("Join Our Community Portal");
        btnPortal.setOnClickListener(v -> openUrl("https://react-9bhsjt.onspace.build"));
        layout.addView(btnPortal);

        Button btnWaChannel = new Button(this);
        btnWaChannel.setText("Follow WhatsApp Channel");
        btnWaChannel.setOnClickListener(v -> openUrl("https://whatsapp.com/channel/0029Va4fpB4GJP8PZEIV5m2e"));
        layout.addView(btnWaChannel);

        Button btnTgChannel = new Button(this);
        btnTgChannel.setText("Join Telegram Channel");
        btnTgChannel.setOnClickListener(v -> openUrl("https://t.me/interestedtechnologyforblind"));
        layout.addView(btnTgChannel);

        Button btnFb = new Button(this);
        btnFb.setText("Follow on Facebook");
        btnFb.setOnClickListener(v -> openUrl("https://facebook.com/Interestedtechnologyforblind"));
        layout.addView(btnFb);

        Button btnYt = new Button(this);
        btnYt.setText("Visit YouTube Channel");
        btnYt.setOnClickListener(v -> openUrl("https://www.youtube.com/@InterestedTechnologyforBlind"));
        layout.addView(btnYt);

        Button btnGithub = new Button(this);
        btnGithub.setText("View GitHub Repository");
        btnGithub.setOnClickListener(v -> openUrl("https://github.com/parasyadav75963-eng/Speech-Plus-tts"));
        layout.addView(btnGithub);

        Button btnEmail = new Button(this);
        btnEmail.setText("Contact via Email");
        btnEmail.setOnClickListener(v -> {
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
            emailIntent.setData(Uri.parse("mailto:contact.i.t.f.b@gmail.com"));
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Speech Plus TTS - Community Inquiry");
            startActivity(Intent.createChooser(emailIntent, "Send Email"));
        });
        layout.addView(btnEmail);

        scrollView.addView(layout);
        setContentView(scrollView);
    }
}
