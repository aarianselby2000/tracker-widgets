package com.tracker.widgets;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Shown by Health Connect when you ask why Tracker Widgets wants your steps. */
public class PrivacyActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        TextView t = new TextView(this);
        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
        t.setPadding(pad, pad, pad, pad);
        t.setTextSize(16);
        t.setText("Tracker Widgets reads your step count from Health Connect only to show it in its Steps widget on your home screen. "
                + "Your steps stay on your phone: they aren't sent to the Tracker, the sheet, or anywhere else.");
        setContentView(t);
    }
}
