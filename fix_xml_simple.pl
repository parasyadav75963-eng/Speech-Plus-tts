use strict;
use warnings;

my $xml_path = "app/src/main/res/layout/activity_main.xml";
open(my $fh, "<", $xml_path) or die "Cannot open XML";
local $/ = undef;
my $xml_content = <$fh>;
close($fh);

# अगर पहले से btnNotificationCenter नहीं है, तो उसे जोड़ें
if ($xml_content !~ /btnNotificationCenter/) {
    my $bell_xml = '        <ImageButton
            android:id="@+id/btnNotificationCenter"
            android:layout_width="44dp"
            android:layout_height="44dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:src="@android:drawable/ic_popup_reminder"
            android:contentDescription="Notifications button, 0 unread notifications. Double tap to view." />';

    # btnVoiceSearch के ठीक बाद जोड़ना
    if ($xml_content =~ s/(android:id="\@\+id\/btnVoiceSearch"[^>]*>.*?<\/ImageButton>)/$1\n$bell_xml/s) {
        print "\n>>> BELL BUTTON ADDED AFTER btnVoiceSearch! <<<\n";
    } elsif ($xml_content =~ s/(android:id="\@\+id\/btnVoiceSearch"[^/]*\/>)/$1\n$bell_xml/s) {
        print "\n>>> BELL BUTTON ADDED AFTER btnVoiceSearch (Self-closing)! <<<\n";
    }
    
    open($fh, ">", $xml_path);
    print $fh $xml_content;
    close($fh);
} else {
    print "\n>>> BELL BUTTON ALREADY EXISTS IN XML! <<<\n";
}
