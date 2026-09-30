package com.speechplus.tts;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        TextView tv = new TextView(this);
        tv.setTextSize(18);
        tv.setPadding(32, 32, 32, 32);
        
        String info = "Speech Plus TTS Engine\n\n"
                    + "Version: 1.1.0\n"
                    + "Last Updated: September 2026\n\n"
                    + "About Interested Technology for Blind:\n"
                    + "Interested Technology for Blind दृष्टिबाधित व्यक्तियों को सशक्त बनाने और डिजिटल तकनीक को पूरी तरह सुलभ (Accessible) बनाने के लिए समर्पित एक मंच है।\n\n"
                    + "Features:\n"
                    + "- Accessibility Audio Routing Support\n"
                    + "- TalkBack Optimized Speech Output\n"
                    + "- Smooth Voice Switching";
                    
        tv.setText(info);
        setContentView(tv);
    }
}
