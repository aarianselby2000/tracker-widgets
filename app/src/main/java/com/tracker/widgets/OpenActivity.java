package com.tracker.widgets;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/** Opens the Tracker at a section (tapping a Money account card), then gets out of the way. */
public class OpenActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String section = getIntent().getStringExtra("section");
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(WidgetData.TRACKER_URL + (section == null ? "" : section))));
        } catch (Exception ignored) {}
        finish();
    }
}
