use strict;
use warnings;

# 1. XML में सुनिश्चित करना कि btnNotificationCenter सही से मौजूद है
my $xml_path = "app/src/main/res/layout/activity_main.xml";
open(my $fh, "<", $xml_path) or die "Cannot open XML";
local $/ = undef;
my $xml_content = <$fh>;
close($fh);

if ($xml_content !> /btnNotificationCenter/) {
    my $bell_button = <<'END_BELL';
        <ImageButton
            android:id="@+id/btnNotificationCenter"
            android:layout_width="44dp"
            android:layout_height="44dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:src="@android:drawable/ic_popup_reminder"
            android:contentDescription="Notifications button, 0 unread notifications. Double tap to view." />
END_BELL
    $xml_content =~ s/(<ImageButton\s+android:id="\@\+id\/btnVoiceSearch"[^>]*>[\s\S]*?<\/ImageButton>)/$1\n$bell_button/;
    open($fh, ">", $xml_path);
    print $fh $xml_content;
    close($fh);
}

# 2. MainActivity.java में btnNotificationCenter का डिक्लेरेशन जोड़ना
my $java_path = "app/src/main/java/com/speechplus/tts/MainActivity.java";
open($fh, "<", $java_path) or die "Cannot open Java";
local $/ = undef;
my $java_content = <$fh>;
close($fh);

if ($java_content !~ /private android\.widget\.ImageButton btnNotificationCenter;/ && $java_content =~ /class MainActivity/) {
    $java_content =~ s/(public class MainActivity extends AppCompatActivity\s*\{)/$1\n    private android.widget.ImageButton btnNotificationCenter;/;
    open($fh, ">", $java_path);
    print $fh $java_content;
    close($fh);
}

print "\n>>> NOTIFICATION ID FIXED SUCCESSFULLY! <<<\n";
