import os, re

# Update Version to 1.5.4 (Code 16)
g = 'app/build.gradle'
if os.path.exists(g):
    with open(g, 'r') as f: d = f.read()
    d = re.sub(r'versionCode \d+', 'versionCode 16', d)
    d = re.sub(r'versionName "[^"]+"', 'versionName "1.5.4"', d)
    with open(g, 'w') as f: f.write(d)

# Clean XML (Remove Whatsapp Numbers and GitHub Button)
x = 'app/src/main/res/layout/activity_main.xml'
if os.path.exists(x):
    with open(x, 'r') as f: d = f.read()
    d = re.sub(r'\s*\(WHATSAPP \+\d+\)', '', d) # Removes numbers
    d = re.sub(r'<Button[^>]*android:text="[^"]*GITHUB[^"]*"[^>]*/>', '', d, flags=re.IGNORECASE) # Removes GitHub link
    with open(x, 'w') as f: f.write(d)

# Hide STOP Button permanently
m = 'app/src/main/java/com/speechplus/tts/MainActivity.java'
if os.path.exists(m):
    with open(m, 'r') as f: d = f.read()
    if 'btnStopSpeaking.setVisibility(View.GONE);' not in d:
        d = d.replace('btnStopSpeaking.setOnClickListener(v -> stopSpeaking());',
                      'btnStopSpeaking.setVisibility(View.GONE);\n        btnStopSpeaking.setOnClickListener(v -> stopSpeaking());')
    with open(m, 'w') as f: f.write(d)

# Fix GitHub Action Filename
w_dir = '.github/workflows/'
if os.path.exists(w_dir):
    for y in os.listdir(w_dir):
        if y.endswith('.yml'):
            yp = w_dir + y
            with open(yp, 'r') as f: d = f.read()
            d = re.sub(r'v1\.[0-9]+\.[0-9]+', 'v1.5.4', d)
            with open(yp, 'w') as f: f.write(d)
