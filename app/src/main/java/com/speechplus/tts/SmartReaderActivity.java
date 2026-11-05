package com.speechplus.tts;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smart_reader);

        prefs = getSharedPreferences("SpeechPlusPrefs", Context.MODE_PRIVATE);
        tvReaderStatus = findViewById(R.id.tvReaderStatus);
        tvPageNumber = findViewById(R.id.tvPageNumber);
        
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        
        // Initialize Sound System on Media Volume
        toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        // Initialize TTS on Media Volume
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                tts.setAudioAttributes(audioAttributes);
                tts.setLanguage(new Locale("hi", "IN")); 
            }
        });

        setupButtons();
    }

    private void setupButtons() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSelectDoc).setOnClickListener(v -> openFilePicker());

        findViewById(R.id.btnPlay).setOnClickListener(v -> playText());
        findViewById(R.id.btnPause).setOnClickListener(v -> pauseText());
        findViewById(R.id.btnRewind).setOnClickListener(v -> Toast.makeText(this, "Rewind coming soon", Toast.LENGTH_SHORT).show());
        findViewById(R.id.btnForward).setOnClickListener(v -> Toast.makeText(this, "Forward coming soon", Toast.LENGTH_SHORT).show());
        
        findViewById(R.id.btnPrevPage).setOnClickListener(v -> changePage(-1));
        findViewById(R.id.btnNextPage).setOnClickListener(v -> changePage(1));
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        String[] mimetypes = {"application/pdf", "image/jpeg", "image/png"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimetypes);
        startActivityForResult(intent, 1001);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            currentDocUri = data.getData();
            if (currentDocUri != null) {
                String mimeType = getContentResolver().getType(currentDocUri);
                isPdf = (mimeType != null && mimeType.equals("application/pdf"));
                currentPage = 0;
                processDocument();
            }
        }
    }

    private void processDocument() {
        tvReaderStatus.setText("Scanning is in progress... Please wait.");
        startScanningSound();
        if (isPdf) {
            extractTextFromPdf();
        } else {
            extractTextFromImage();
        }
    }

    private void startScanningSound() {
        isScanning = true;
        new Thread(() -> {
            while (isScanning) {
                if(prefs.getBoolean("sound_scan_progress", true)) {
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 100); 
                }
                try { Thread.sleep(1000); } catch (InterruptedException e) {}
            }
        }).start();
    }

    private void stopScanningSound(boolean success) {
        isScanning = false;
        if (success) {
            if(prefs.getBoolean("sound_scan_complete", true)) {
                toneGen.startTone(ToneGenerator.TONE_PROP_PROMPT, 200); 
            }
        } else {
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 200); 
        }
    }

    private void changePage(int offset) {
        if (!isPdf || currentDocUri == null) {
            Toast.makeText(this, "Page navigation is only for PDFs!", Toast.LENGTH_SHORT).show();
            return;
        }
        int newPage = currentPage + offset;
        if (newPage >= 0 && (totalPages == 0 || newPage < totalPages)) {
            if(prefs.getBoolean("sound_page_turn", true)) {
                toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 150); 
            }
            currentPage = newPage;
            pauseText();
            processDocument();
        } else {
            Toast.makeText(this, "No more pages!", Toast.LENGTH_SHORT).show();
        }
    }

    private void extractTextFromImage() {
        try {
            InputImage image = InputImage.fromFilePath(this, currentDocUri);
            recognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        extractedText = visionText.getText();
                        tvReaderStatus.setText("Scan Complete! Ready to play.");
                        stopScanningSound(true);
                        if (prefs.getBoolean("reader_autostart", true)) playText();
                    })
                    .addOnFailureListener(e -> {
                        tvReaderStatus.setText("Scan Failed.");
                        stopScanningSound(false);
                    });
        } catch (Exception e) {
            tvReaderStatus.setText("Error loading image.");
            stopScanningSound(false);
        }
    }

    private void extractTextFromPdf() {
        new Thread(() -> {
            try {
                ParcelFileDescriptor fd = getContentResolver().openFileDescriptor(currentDocUri, "r");
                if (fd != null) {
                    PdfRenderer renderer = new PdfRenderer(fd);
                    totalPages = renderer.getPageCount();
                    runOnUiThread(() -> tvPageNumber.setText("Page: " + (currentPage + 1) + " / " + totalPages));

                    if (totalPages > 0 && currentPage < totalPages) {
                        PdfRenderer.Page page = renderer.openPage(currentPage);
                        Bitmap bitmap = Bitmap.createBitmap(page.getWidth() * 2, page.getHeight() * 2, Bitmap.Config.ARGB_8888);
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        page.close();

                        InputImage image = InputImage.fromBitmap(bitmap, 0);
                        runOnUiThread(() -> {
                            recognizer.process(image)
                                    .addOnSuccessListener(visionText -> {
                                        extractedText = visionText.getText();
                                        tvReaderStatus.setText("Page " + (currentPage + 1) + " Scan Complete!");
                                        stopScanningSound(true);
                                        if (prefs.getBoolean("reader_autostart", true)) playText();
                                    })
                                    .addOnFailureListener(e -> {
                                        tvReaderStatus.setText("PDF Scan Failed.");
                                        stopScanningSound(false);
                                    });
                        });
                    }
                    renderer.close();
                    fd.close();
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvReaderStatus.setText("Error reading PDF.");
                    stopScanningSound(false);
                });
            }
        }).start();
    }

    private void playText() {
        if (extractedText.isEmpty()) {
            Toast.makeText(this, "Nothing to play yet!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (tts != null) {
            tts.speak(extractedText, TextToSpeech.QUEUE_FLUSH, null, "SMART_READER_ID");
            Toast.makeText(this, "Playing Document...", Toast.LENGTH_SHORT).show();
        }
    }

    private void pauseText() {
        if (tts != null && tts.isSpeaking()) {
            tts.stop();
            Toast.makeText(this, "Paused", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (toneGen != null) {
            toneGen.release();
        }
        super.onDestroy();
    }
}
