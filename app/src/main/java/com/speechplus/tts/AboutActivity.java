package com.speechplus.tts;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 40, 40, 40);

        TextView titleTv = new TextView(this);
        titleTv.setText("Speech Plus TTS Engine");
        titleTv.setTextSize(22);
        titleTv.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(titleTv);

        TextView verTv = new TextView(this);
        verTv.setText("Version: 1.2.0 (Latest Release)\nDeveloper: Paras Yadav\nCommunity: Interested Technology for Blind\nLast Updated: September 2026\n");
        verTv.setTextSize(16);
        layout.addView(verTv);

        TextView aboutTv = new TextView(this);
        aboutTv.setText("About Us:\nInterested Technology for Blind is a dedicated community initiative creating fully accessible and barrier-free digital assistive technologies for screen reader and TalkBack users worldwide.\n\nKey Highlights:\n• Accessibility Stream Audio Routing\n• Character & Keyboard Echo Reading Support\n• Dynamic Multi-language & Voice Selection\n• TalkBack Synchronized Speech Rate\n");
        aboutTv.setTextSize(15);
        layout.addView(aboutTv);

        Button btnEmail = new Button(this);
        btnEmail.setText("Send Feedback via Email");
        btnEmail.setOnClickListener(v -> {
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
            emailIntent.setData(Uri.parse("mailto:contact.itfb@gmail.com"));
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Speech Plus TTS Feedback");
            startActivity(Intent.createChooser(emailIntent, "Send Email"));
        });
        layout.addView(btnEmail);

        Button btnYoutube = new Button(this);
        btnYoutube.setText("Subscribe to YouTube Channel");
        btnYoutube.setOnClickListener(v -> {
            Intent ytIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/@InterestedTechnologyforBlind"));
            startActivity(ytIntent);
        });
        layout.addView(btnYoutube);

        Button btnTelegram = new Button(this);
        btnTelegram.setText("Join Official Telegram Community");
        btnTelegram.setOnClickListener(v -> {
            Intent tgIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/InterestedTechnologyForBlind"));
            startActivity(tgIntent);
        });
        layout.addView(btnTelegram);

        Button btnGithub = new Button(this);
        btnGithub.setText("Visit GitHub Repository");
        btnGithub.setOnClickListener(v -> {
            Intent ghIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/parasyadav75963-eng/Speech-Plus-tts"));
            startActivity(ghIntent);
        });
        layout.addView(btnGithub);

        scrollView.addView(layout);
        setContentView(scrollView);
    }
}
