package com.speechplus.tts;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        Button btnSend = findViewById(R.id.btnSendFeedback);
        Button btnBack = findViewById(R.id.btnBack);

        btnSend.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:contact.itfb@gmail.com"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "Speech Plus TTS Feedback");
            try {
                startActivity(Intent.createChooser(intent, "Send Email..."));
            } catch (Exception ignored) {}
        });

        btnBack.setOnClickListener(v -> finish());
    }
}
