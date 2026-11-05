package com.speechplus.tts;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.speech.tts.TextToSpeech;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.Locale;

public class SmartReaderActivity extends Activity {
    private TextView tvReaderStatus, tvPageNumber;
    private TextRecognizer recognizer;
    private String extractedText = "";
    
    // Page Management
    private int currentPage = 0;
    private int totalPages = 0;
    private Uri currentDocUri = null;
    private boolean isPdf = false;

    // Sounds & TTS
    private ToneGenerator toneGen;
    private boolean isScanning = false;
    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smart_reader);

        tvReaderStatus = findViewById(R.id.tvReaderStatus);
        tvPageNumber = findViewById(R.id.tvPageNumber);
        
        // Initialize OCR Engine
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        
        // Initialize Sound System (ToneGenerator for Accessibility Volume)
        toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        // Initialize TTS
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                tts.setAudioAttributes(audioAttributes);
                tts.setLanguage(new Locale("hi", "IN")); // Hindi & English support
            }
        });

        setupButtons();
    }

    private void setupButtons() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSelectDoc).setOnClickListener(v -> openFilePicker());

        // Playback Controls
        findViewById(R.id.btnPlay).setOnClickListener(v -> playText());
        findViewById(R.id.btnPause).setOnClickListener(v -> pauseText());
        findViewById(R.id.btnRewind).setOnClickListener(v -> Toast.makeText(this, "Rewind coming soon", Toast.LENGTH_SHORT).show());
        findViewById(R.id.btnForward).setOnClickListener(v -> Toast.makeText(this, "Forward coming soon", Toast.LENGTH_SHORT).show());
        
        // Page Navigation
        findViewById(R.id.btnPrevPage).setOnClickListener(v -> changePage(-1));
        findViewById(R.id.btnNextPage).setOnClickListener(v -> changePage(1));
    }
