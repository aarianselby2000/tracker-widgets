package com.tracker.widgets;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The forms the widgets open, styled like the Tracker: Add food (or a photo of food, a label or a receipt),
 * Add entry (Money's own fields, or a receipt photo) and Quick add (Inbox).
 */
public class EntryActivity extends Activity {
    // The Tracker's dark theme.
    static final int INK = 0xFFE9EDF2, MUTED = 0xFF9AA4B1, SUNK = 0xFF12171D, LINE = 0xFF2A313B, CHIP = 0xFF232A33, ON = 0xFF12161B;
    static final int HEALTH = 0xFFEF6A6A, MONEY = 0xFF55B981, INBOX = 0xFF5BB3F0;

    static final List<String> CATS = Arrays.asList("Food", "Supplies", "Gifts", "Fun", "Bill");
    static final List<String> INCOME = Arrays.asList("Paycheck", "From family", "Paid back", "Financial aid", "Refund", "Gift received", "Other income");
    static final List<String> MOVES = Arrays.asList("To savings", "From savings", "ATM withdrawal", "Cash deposit");
    static final String[] CARD_VALUES = {"Self", "Mom", "Dad", "Cash"};
    static final String[] CARD_LABELS = {"My card", "Mom", "Dad", "Cash"};
    static final int REQ_CAMERA = 1, REQ_PICK = 2;

    private LinearLayout box;
    private String mode;
    private int accent;
    private Typeface cond;

    // photo state
    private String photoB64;
    private File camFile;
    private LinearLayout photoBox, payRow;
    private ImageView preview;
    private TextView photoMsg;
    private String pay = "Self";
    private final List<View> manualOnly = new ArrayList<>();
    private EditText mainText;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        mode = getIntent().getStringExtra("mode");
        if (mode == null) mode = "food";
        accent = mode.equals("money") ? MONEY : mode.equals("quick") ? INBOX : HEALTH;
        cond = Typeface.create("sans-serif-condensed", Typeface.BOLD);
        ScrollView scroll = new ScrollView(this);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(18), dp(20), dp(18));
        scroll.addView(box);
        setContentView(scroll, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (b != null) {
            photoB64 = b.getString("photo");
            String cf = b.getString("camFile");
            if (cf != null) camFile = new File(cf);
            pay = b.getString("pay", "Self");
        }
        if (!WidgetData.canSend(this)) {
            header("SET UP FIRST", R.drawable.ic_inbox);
            note("Open the Tracker Widgets app and paste your phone link, then try again.");
            primary("OK", this::finish);
            return;
        }
        if (mode.equals("money")) money(getIntent().getStringExtra("dir"));
        else if (mode.equals("quick")) quick();
        else food();
        if (b == null && getIntent().getBooleanExtra("camera", false)) takePhoto();
        else if (photoB64 != null) showPhoto(null);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (photoB64 != null && photoB64.length() < 400000) out.putString("photo", photoB64);
        if (camFile != null) out.putString("camFile", camFile.getAbsolutePath());
        out.putString("pay", pay);
    }

    // ---------- Health: Add food ----------

    private void food() {
        header("ADD FOOD", R.drawable.ic_health);
        mainText = field("What did you eat or drink? e.g. 2 eggs, toast, OJ", true);
        hint("Logged at this time. The Tracker estimates calories and nutrients the next time it's open.");
        photoSection("Or a photo of the food, a nutrition label or a receipt");
        primary("Add", () -> {
            String s = mainText.getText().toString().trim();
            if (photoB64 != null) { sendPhoto(s); return; }
            if (s.isEmpty()) { mainText.setError("Type what you ate, or add a photo"); return; }
            send(new JSONObject().put("op", "food").put("text", cut(s, 300)), "Added. The Tracker estimates it when it next opens.");
        });
    }

    // ---------- Inbox: Quick add ----------

    private void quick() {
        header("QUICK ADD", R.drawable.ic_inbox);
        mainText = field("A plan, a purchase, an assignment, food… e.g. dinner with Sam Fri 7pm", true);
        hint("Claude sorts it into the right section the next time the Tracker checks, and it shows up in the Inbox widget to add with one tap.");
        primary("Send to the Inbox", () -> {
            String s = mainText.getText().toString().trim();
            if (s.isEmpty()) { mainText.setError("Type something first"); return; }
            send(new JSONObject().put("op", "quick").put("text", cut(s, 300)), "Sent to the Inbox.");
        });
    }

    // ---------- Money: Add entry (the same fields as the Tracker's form) ----------

    private LocalDate date = LocalDate.now();
    private String dir = "out", type = "Food", card = "Self";

    private void money(String dir0) {
        header("ADD ENTRY", R.drawable.ic_money);
        JSONObject m = WidgetData.w(this).optJSONObject("m");
        List<String> cats = list(m, "cats", CATS), income = list(m, "income", INCOME), moves = list(m, "moves", MOVES);
        dir = dir0 == null ? "out" : dir0;

        Flow seg = new Flow(this);
        String[][] dirs = {{"out", "− Spent"}, {"in", "＋ Received"}, {"move", "⇄ Moved"}};
        box.addView(seg, full());
        manualOnly.add(seg);

        TextView dateChip = chip("", true);
        Runnable showDate = () -> dateChip.setText("📅  " + (date.equals(LocalDate.now()) ? "Today, " : "") + date.format(DateTimeFormatter.ofPattern("EEE MMM d", Locale.US)));
        showDate.run();
        dateChip.setOnClickListener(v -> new DatePickerDialog(this, android.R.style.Theme_Material_Dialog, (dp, y, mo, d) -> { date = LocalDate.of(y, mo + 1, d); showDate.run(); },
                date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth()).show());
        LinearLayout.LayoutParams dl = wrap();
        dl.topMargin = dp(10);
        box.addView(dateChip, dl);
        manualOnly.add(dateChip);

        EditText amount = field("Amount ($)", false);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        amount.setTypeface(cond);
        manualOnly.add(amount);
        EditText source = field("Description, e.g. Chick Fil A", false);
        source.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        manualOnly.add(source);

        TextView typeLabel = label("CATEGORY");
        manualOnly.add(typeLabel);
        Flow types = new Flow(this);
        box.addView(types, full());
        manualOnly.add(types);
        TextView cardLabel = label("CARD");
        Flow cards = new Flow(this);
        box.addView(cards, full());

        Runnable[] redraw = new Runnable[1];
        redraw[0] = () -> {
            seg.removeAllViews();
            for (String[] d : dirs) {
                TextView t = chip(d[1], d[0].equals(dir));
                t.setOnClickListener(v -> { dir = d[0]; type = null; redraw[0].run(); });
                seg.addView(t);
            }
            List<String> opts = dir.equals("in") ? income : dir.equals("move") ? moves : cats;
            if (type == null || !opts.contains(type)) type = dir.equals("in") ? opts.get(opts.size() - 1) : opts.get(0);
            typeLabel.setText(dir.equals("in") ? "TYPE" : dir.equals("move") ? "MOVE" : "CATEGORY");
            types.removeAllViews();
            for (String o : opts) {
                TextView t = chip(o, o.equals(type));
                t.setOnClickListener(v -> { type = o; redraw[0].run(); });
                types.addView(t);
            }
            boolean photo = photoB64 != null;
            for (View v : manualOnly) v.setVisibility(photo ? View.GONE : View.VISIBLE);
            int cv = dir.equals("move") && !photo ? View.GONE : View.VISIBLE;
            cardLabel.setVisibility(cv);
            cards.setVisibility(cv);
            cardLabel.setText(photo ? "PAID WITH" : "CARD");
            cards.removeAllViews();
            for (int i = 0; i < CARD_VALUES.length; i++) {
                String val = CARD_VALUES[i];
                TextView t = chip(CARD_LABELS[i], val.equals(card));
                t.setOnClickListener(v -> { card = val; pay = val; redraw[0].run(); });
                cards.addView(t);
            }
        };
        redraw[0].run();
        moneyRedraw = redraw[0];
        if (!getIntent().getBooleanExtra("camera", false)) amount.requestFocus();

        photoSection("Or snap the receipt: Claude reads the total (and logs any food on it)");
        primary("Add entry", () -> {
            if (photoB64 != null) { pay = card; sendPhoto(""); return; }
            double a;
            try { a = Double.parseDouble(amount.getText().toString().replace("$", "").replace(",", "").trim()); } catch (Exception e) { a = 0; }
            if (!(a > 0)) { amount.setError("Enter an amount above $0"); return; }
            String s = source.getText().toString().trim();
            if (s.isEmpty()) { source.setError(dir.equals("out") ? "Add a short description, like where you bought it" : "Add a short description, like where it came from"); return; }
            send(new JSONObject().put("op", "money").put("dir", dir).put("date", date.toString()).put("amount", Math.round(a * 100) / 100.0)
                    .put("type", type).put("source", cut(s, 80)).put("card", dir.equals("move") ? "Self" : card), "Added " + s + ". It goes into Money when the Tracker next opens.");
        });
    }

    private Runnable moneyRedraw;

    // ---------- photos ----------

    private void photoSection(String title) {
        TextView l = label(title.toUpperCase(Locale.US));
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(iconButton("Take photo", R.drawable.ic_camera, v -> takePhoto()), weight(1, dp(6)));
        row.addView(iconButton("Choose photo", R.drawable.ic_photos, v -> pickPhoto()), weight(1, 0));
        LinearLayout.LayoutParams rl = full();
        rl.topMargin = dp(6);
        box.addView(row, rl);

        photoBox = new LinearLayout(this);
        photoBox.setOrientation(LinearLayout.VERTICAL);
        photoBox.setVisibility(View.GONE);
        FrameLayout frame = new FrameLayout(this);
        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackground(round(SUNK, LINE, 12));
        preview.setClipToOutline(true);
        frame.addView(preview, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(170)));
        TextView remove = chip("✕  Remove", false);
        remove.setOnClickListener(v -> { photoB64 = null; photoBox.setVisibility(View.GONE); if (moneyRedraw != null) moneyRedraw.run(); if (mainText != null) mainText.setHint(mode.equals("food") ? "What did you eat or drink? e.g. 2 eggs, toast, OJ" : mainText.getHint()); });
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        rp.setMargins(0, dp(8), dp(8), 0);
        frame.addView(remove, rp);
        LinearLayout.LayoutParams fl = full();
        fl.topMargin = dp(10);
        photoBox.addView(frame, fl);
        photoMsg = new TextView(this);
        photoMsg.setTextColor(MUTED);
        photoMsg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        photoBox.addView(photoMsg, full());
        if (mode.equals("food")) {
            TextView pl = new TextView(this);
            pl.setText("IF IT'S A RECEIPT, PAID WITH");
            pl.setTypeface(cond);
            pl.setTextColor(MUTED);
            pl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            LinearLayout.LayoutParams pll = full();
            pll.topMargin = dp(8);
            photoBox.addView(pl, pll);
            payRow = new Flow(this);
            photoBox.addView(payRow, full());
            drawPay();
        }
        box.addView(photoBox, full());
    }

    private void drawPay() {
        if (payRow == null) return;
        payRow.removeAllViews();
        String[] vals = {"Self", "Mom", "Dad", "Cash", "skip"}, labels = {"My card", "Mom", "Dad", "Cash", "Not in Money"};
        for (int i = 0; i < vals.length; i++) {
            String val = vals[i];
            TextView t = chip(labels[i], val.equals(pay));
            t.setOnClickListener(v -> { pay = val; drawPay(); });
            payRow.addView(t);
        }
    }

    private void takePhoto() {
        try {
            File dir = new File(getCacheDir(), "photos");
            dir.mkdirs();
            camFile = new File(dir, "photo-" + System.currentTimeMillis() + ".jpg");
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", camFile);
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, uri)
                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(i, REQ_CAMERA);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No camera app found. Choose a photo instead.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't open the camera (" + e.getClass().getSimpleName() + ").", Toast.LENGTH_LONG).show();
        }
    }

    private void pickPhoto() {
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(i, "Choose a photo"), REQ_PICK);
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't open your photos.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK) return;
        Uri src = req == REQ_CAMERA ? (camFile != null ? Uri.fromFile(camFile) : null) : data != null ? data.getData() : null;
        if (src == null) return;
        if (photoBox != null) { photoBox.setVisibility(View.VISIBLE); photoMsg.setText("Getting the photo ready…"); }
        new Thread(() -> {
            String b64 = null;
            Bitmap shown = null;
            try {
                Bitmap bm = load(src);
                shown = bm;
                b64 = encode(bm);
            } catch (Exception ignored) {}
            final String out = b64;
            final Bitmap view = shown;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (out == null) { Toast.makeText(this, "Couldn't read that photo.", Toast.LENGTH_LONG).show(); if (photoB64 == null && photoBox != null) photoBox.setVisibility(View.GONE); return; }
                photoB64 = out;
                showPhoto(view);
            });
        }).start();
    }

    private void showPhoto(Bitmap bm) {
        if (photoBox == null) return;
        photoBox.setVisibility(View.VISIBLE);
        if (bm == null && photoB64 != null) {
            byte[] raw = Base64.decode(photoB64, Base64.NO_WRAP);
            bm = BitmapFactory.decodeByteArray(raw, 0, raw.length);
        }
        if (bm != null) preview.setImageBitmap(bm);
        photoMsg.setText(mode.equals("money") ? "Claude reads the total when the Tracker next opens, and adds it to Money with the card below."
                : "Claude reads it when the Tracker next opens: food is estimated from the picture, a label's numbers are read, and a receipt's total goes to Money.");
        if (mainText != null && mode.equals("food")) mainText.setHint("Note (optional): e.g. ate half, shared the fries");
        if (moneyRedraw != null) moneyRedraw.run();
    }

    /** Reads a photo at a size close to what's sent, turned the right way up. */
    private Bitmap load(Uri uri) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, o); }
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= 1400) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        Bitmap bm;
        try (InputStream in = getContentResolver().openInputStream(uri)) { bm = BitmapFactory.decodeStream(in, null, o2); }
        if (bm == null) throw new Exception("not an image");
        int rot = 0;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            int ori = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            rot = ori == ExifInterface.ORIENTATION_ROTATE_90 ? 90 : ori == ExifInterface.ORIENTATION_ROTATE_180 ? 180 : ori == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
        } catch (Exception ignored) {}
        float scale = Math.min(1f, 1280f / Math.max(bm.getWidth(), bm.getHeight()));
        if (rot != 0 || scale < 1f) {
            Matrix mx = new Matrix();
            mx.postScale(scale, scale);
            mx.postRotate(rot);
            bm = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), mx, true);
        }
        return bm;
    }

    /** JPEG, small enough for the sheet (about 220 KB at most), as base64. */
    private static String encode(Bitmap bm) {
        int q = 82;
        Bitmap cur = bm;
        for (int tries = 0; tries < 12; tries++) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            cur.compress(Bitmap.CompressFormat.JPEG, q, out);
            String b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
            if (b64.length() <= 290000) return b64;
            if (q > 55) q -= 9;
            else cur = Bitmap.createScaledBitmap(cur, Math.round(cur.getWidth() * 0.8f), Math.round(cur.getHeight() * 0.8f), true);
        }
        return null;
    }

    private void sendPhoto(String note) {
        try {
            send(new JSONObject().put("op", "photo").put("note", cut(note, 200)).put("pay", pay).put("img", photoB64),
                    "Photo added. Claude reads it when the Tracker next opens.");
        } catch (Exception ignored) {}
    }

    // ---------- sending ----------

    interface Go { void run() throws Exception; }

    private void send(JSONObject e, String msg) {
        Phone.send(this, e);
        Widgets.updateAll(this);
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        finish();
    }

    // ---------- the Tracker's look ----------

    private void header(String title, int icon) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(round(soft(accent), withAlpha(accent, 0x55), 10));
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER);
        badge.addView(iv, ip);
        row.addView(badge, new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView t = new TextView(this);
        t.setText(title);
        t.setTypeface(cond);
        t.setTextColor(accent);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        t.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tl.leftMargin = dp(12);
        row.addView(t, tl);
        TextView x = new TextView(this);
        x.setText("✕");
        x.setTextColor(MUTED);
        x.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        x.setPadding(dp(10), dp(4), dp(4), dp(4));
        x.setOnClickListener(v -> finish());
        row.addView(x);
        box.addView(row, full());
    }

    private EditText field(String hint, boolean multi) {
        EditText t = new EditText(this);
        t.setHint(hint);
        t.setHintTextColor(0xFF6B7380);
        t.setTextColor(INK);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setBackground(round(SUNK, LINE, 10));
        t.setPadding(dp(14), dp(12), dp(14), dp(12));
        if (multi) {
            t.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            t.setMinLines(2);
            t.setGravity(Gravity.TOP | Gravity.START);
        }
        LinearLayout.LayoutParams lp = full();
        lp.topMargin = dp(12);
        box.addView(t, lp);
        return t;
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTypeface(cond);
        t.setTextColor(MUTED);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams lp = full();
        lp.topMargin = dp(14);
        box.addView(t, lp);
        return t;
    }

    private void hint(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(MUTED);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams lp = full();
        lp.topMargin = dp(6);
        box.addView(t, lp);
    }

    private void note(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(INK);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        LinearLayout.LayoutParams lp = full();
        lp.topMargin = dp(12);
        box.addView(t, lp);
    }

    private TextView chip(String text, boolean on) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(on ? accent : INK);
        t.setBackground(round(on ? soft(accent) : CHIP, on ? accent : CHIP, 18));
        t.setPadding(dp(13), dp(8), dp(13), dp(8));
        return t;
    }

    private View iconButton(String text, int icon, View.OnClickListener go) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER);
        b.setBackground(round(soft(accent), withAlpha(accent, 0x66), 10));
        b.setPadding(dp(8), dp(11), dp(8), dp(11));
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(accent);
        b.addView(iv, new LinearLayout.LayoutParams(dp(18), dp(18)));
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(accent);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LinearLayout.LayoutParams tl = wrap();
        tl.leftMargin = dp(7);
        b.addView(t, tl);
        b.setOnClickListener(go);
        return b;
    }

    private void primary(String text, Go go) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(ON);
        b.setTypeface(cond);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        b.setLetterSpacing(0.04f);
        b.setBackground(round(accent, accent, 12));
        b.setPadding(0, dp(13), 0, dp(13));
        b.setOnClickListener(v -> { try { go.run(); } catch (Exception ignored) {} });
        LinearLayout.LayoutParams lp = full();
        lp.topMargin = dp(18);
        box.addView(b, lp);
    }

    private GradientDrawable round(int fill, int stroke, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(1), stroke);
        return g;
    }

    private static int soft(int c) { return withAlpha(c, 0x2E); }
    private static int withAlpha(int c, int a) { return Color.argb(a, Color.red(c), Color.green(c), Color.blue(c)); }

    private static String cut(String s, int n) { return s.length() > n ? s.substring(0, n) : s; }

    private static List<String> list(JSONObject m, String key, List<String> fallback) {
        JSONArray a = m != null ? m.optJSONArray(key) : null;
        if (a == null || a.length() == 0) return fallback;
        List<String> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
        return out;
    }

    private LinearLayout.LayoutParams full() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams wrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams weight(float w, int right) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
        lp.rightMargin = right;
        return lp;
    }

    private int dp(float x) { return Math.round(x * getResources().getDisplayMetrics().density); }

    /** Chips that wrap onto new lines, like the Tracker's pill rows. */
    static class Flow extends LinearLayout {
        Flow(android.content.Context c) { super(c); }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int maxW = MeasureSpec.getSize(wSpec), gap = Math.round(7 * getResources().getDisplayMetrics().density);
            int x = 0, y = 0, rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View ch = getChildAt(i);
                if (ch.getVisibility() == GONE) continue;
                ch.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                if (x > 0 && x + ch.getMeasuredWidth() > maxW) { x = 0; y += rowH + gap; rowH = 0; }
                x += ch.getMeasuredWidth() + gap;
                rowH = Math.max(rowH, ch.getMeasuredHeight());
            }
            setMeasuredDimension(maxW, y + rowH + gap);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int maxW = r - l, gap = Math.round(7 * getResources().getDisplayMetrics().density);
            int x = 0, y = gap, rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View ch = getChildAt(i);
                if (ch.getVisibility() == GONE) continue;
                if (x > 0 && x + ch.getMeasuredWidth() > maxW) { x = 0; y += rowH + gap; rowH = 0; }
                ch.layout(x, y, x + ch.getMeasuredWidth(), y + ch.getMeasuredHeight());
                x += ch.getMeasuredWidth() + gap;
                rowH = Math.max(rowH, ch.getMeasuredHeight());
            }
        }
    }
}
