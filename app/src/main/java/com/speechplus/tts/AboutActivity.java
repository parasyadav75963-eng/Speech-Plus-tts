package com.speechplus.tts;

import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setTextSize(16);
        tv.setPadding(36, 36, 36, 36);

        String info = "Speech Plus TTS Engine\n\n"
                + "Version: 1.1.0\n"
                + "Last Updated: September 2026\n\n"
                + "----------------------------------------\n"
                + "About Interested Technology for Blind:\n"
                + "Interested Technology for Blind is a dedicated community initiative aimed at empowering visually impaired individuals through accessible digital solutions, assistive technologies, and optimized tools for screen reader users.\n\n"
                + "Key Highlights:\n"
                + "• Seamless accessibility audio stream routing\n"
                + "• TalkBack-friendly fast speech synthesis\n"
                + "• Forwarding to preferred target TTS engines\n\n"
                + "Developer & Support Contact:\n"
                + "Email: parasyadav75963@gmail.com\n"
                + "GitHub: https://github.com/parasyadav75963-eng/Speech-Plus-tts\n"
                + "----------------------------------------";

        tv.setText(info);
        scrollView.addView(tv);
        setContentView(scrollView);
    }
}
