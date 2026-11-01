use strict;
use warnings;

my $xml_path = "app/src/main/res/layout/activity_main.xml";
open(my $fh, "<", $xml_path) or die "Cannot open XML";
local $/ = undef;
my $xml_content = <$fh>;
close($fh);

# पुराने सभी डुप्लीकेट या टूटे हुए बेल बटन हटाएँ
my $bell_pattern = qr/<ImageButton[^>]*id="\@\+id\/btnNotificationCenter"[^>]*>[\s\S]*?<\/ImageButton>|<ImageButton[^>]*id="\@\+id\/btnNotificationCenter"[^/]*\/>/s;
$xml_content =~ s/$bell_pattern//g;

# नया साफ और सही बेल बटन तैयार करें
my $bell_xml = '        <ImageButton
            android:id="@+id/btnNotificationCenter"
            android:layout_width="44dp"
            android:layout_height="44dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:src="@android:drawable/ic_popup_reminder"
            android:contentDescription="Notifications button, 0 unread notifications. Double tap to view." />';

# btnVoiceSearch के ठीक बाद इंसर्ट करें
if ($xml_content =~ /(<ImageButton\s+android:id="\@\+id\/btnVoiceSearch"[^>]*>[\s\S]*?<\/ImageButton>)/) {
    my $match = $1;
    $xml_content =~ s/\Q$match\E/$match\n$bell_xml/;
} elsif ($xml_content =~ /(<ImageButton\s+android:id="\@\+id\/btnVoiceSearch"[^/]*\/>)/) {
    my $match = $1;
    $xml_content =~ s/\Q$match\E/$match\n$bell_xml/;
}

open($fh, ">", $xml_path);
print $fh $xml_content;
close($fh);

print "\n>>> XML NOTIFICATION BUTTON FIXED PERFECTLY! <<<\n";
