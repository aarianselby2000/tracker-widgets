package com.tracker.widgets;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The small forms the widgets open: Add food (Health), Add entry (Money, the same fields as the Tracker's form),
 * and Quick add (Inbox: anything, sorted by Claude when the Tracker next checks).
 */
public class EntryActivity extends Activity {
    private LinearLayout box;
    private String mode;

    // Money's lists, used when the Tracker hasn't sent its own yet.
    static final List<String> CATS = Arrays.asList("Food", "Supplies", "Gifts", "Fun", "Bill");
    static final List<String> INCOME = Arrays.asList("Paycheck", "From family", "Paid back", "Financial aid", "Refund", "Gift received", "Other income");
    static final List<String> MOVES = Arrays.asList("To savings", "From savings", "ATM withdrawal", "Cash deposit");
    static final String[] CARD_VALUES = {"Self", "Mom", "Dad", "Cash"};
    static final String[] CARD_LABELS = {"My card", "Mom", "Dad", "Cash"};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        mode = getIntent().getStringExtra("mode");
        if (mode == null) mode = "food";
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        box.setPadding(pad, dp(8), pad, dp(12));
        setContentView(box, new ViewGroup.LayoutParams(dp(330), ViewGroup.LayoutParams.WRAP_CONTENT));
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        if (!WidgetData.canSend(this)) {
            setTitle("Set up first");
            note("Open the Tracker Widgets app and paste the phone link, then try again.");
            buttons("OK", null, null);
            return;
        }
        if (mode.equals("money")) money(getIntent().getStringExtra("dir"));
        else if (mode.equals("quick")) textForm("Quick add", "A plan, a purchase, an assignment, food… e.g. \"dinner with Sam Fri 7pm\"",
                "Claude sorts it into the right section the next time the Tracker checks, and it shows up in the Inbox widget to add.", "quick");
        else textForm("Add food", "What did you eat or drink? e.g. 2 eggs, toast, OJ",
                "Logged at the time you add it. The Tracker estimates calories and nutrients the next time it's open.", "food");
    }

    private void textForm(String title, String hint, String help, String op) {
        setTitle(title);
        EditText t = new EditText(this);
        t.setHint(hint);
        t.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        t.setMinLines(2);
        box.addView(t, full());
        note(help);
        t.requestFocus();
        buttons(op.equals("food") ? "Add food" : "Add", () -> {
            String s = t.getText().toString().trim();
            if (s.isEmpty()) { t.setError("Type something first"); return false; }
            JSONObject e = new JSONObject();
            try { e.put("op", op).put("text", s.length() > 300 ? s.substring(0, 300) : s); } catch (Exception ignored) {}
            Phone.send(this, e);
            Toast.makeText(this, op.equals("food") ? "Added. The Tracker estimates it when it next opens." : "Sent to the Inbox.", Toast.LENGTH_SHORT).show();
            return true;
        }, "Cancel");
    }

    // ---------- Money: the same fields as the Tracker's Add entry form ----------

    private LocalDate date = LocalDate.now();

    private void money(String dir0) {
        setTitle("Add entry");
        JSONObject m = WidgetData.w(this).optJSONObject("m");
        List<String> cats = list(m, "cats", CATS), income = list(m, "income", INCOME), moves = list(m, "moves", MOVES);

        RadioGroup dir = new RadioGroup(this);
        dir.setOrientation(RadioGroup.HORIZONTAL);
        String[][] dirs = {{"out", "Spent"}, {"in", "Received"}, {"move", "Moved"}};
        for (String[] d : dirs) {
            RadioButton r = new RadioButton(this);
            r.setText(d[1]);
            r.setTag(d[0]);
            r.setId(View.generateViewId());
            dir.addView(r);
            if (d[0].equals(dir0 == null ? "out" : dir0)) r.setChecked(true);
        }
        box.addView(dir, full());

        Button dateBtn = new Button(this);
        dateBtn.setAllCaps(false);
        Runnable showDate = () -> dateBtn.setText("Date: " + (date.equals(LocalDate.now()) ? "Today, " : "") + date.format(DateTimeFormatter.ofPattern("EEE MMM d", Locale.US)));
        showDate.run();
        dateBtn.setOnClickListener(v -> new DatePickerDialog(this, (dp, y, mo, d) -> { date = LocalDate.of(y, mo + 1, d); showDate.run(); },
                date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth()).show());
        box.addView(dateBtn, full());

        EditText amount = new EditText(this);
        amount.setHint("Amount ($)");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        box.addView(amount, full());

        EditText source = new EditText(this);
        source.setHint("Description, e.g. Chick Fil A");
        source.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        box.addView(source, full());

        TextView typeLabel = label("Category");
        Spinner type = new Spinner(this);
        box.addView(type, full());
        TextView cardLabel = label("Card");
        Spinner card = new Spinner(this);
        card.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, CARD_LABELS));
        card.setSelection(0);
        box.addView(card, full());

        Runnable byDir = () -> {
            String d = currentDir(dir);
            List<String> opts = d.equals("in") ? income : d.equals("move") ? moves : cats;
            type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, opts));
            typeLabel.setText(d.equals("in") ? "Type" : d.equals("move") ? "Move" : "Category");
            int vis = d.equals("move") ? View.GONE : View.VISIBLE;
            card.setVisibility(vis);
            cardLabel.setVisibility(vis);
        };
        byDir.run();
        dir.setOnCheckedChangeListener((g, id) -> byDir.run());
        amount.requestFocus();

        buttons("Add entry", () -> {
            double a;
            try { a = Double.parseDouble(amount.getText().toString().replace("$", "").replace(",", "").trim()); } catch (Exception e) { a = 0; }
            if (!(a > 0)) { amount.setError("Enter an amount above $0"); return false; }
            String d = currentDir(dir), s = source.getText().toString().trim();
            if (s.isEmpty()) { source.setError(d.equals("out") ? "Add a short description, like where you bought it" : "Add a short description, like where it came from"); return false; }
            JSONObject e = new JSONObject();
            try {
                e.put("op", "money").put("dir", d).put("date", date.toString()).put("amount", Math.round(a * 100) / 100.0)
                        .put("type", String.valueOf(type.getSelectedItem())).put("source", s)
                        .put("card", d.equals("move") ? "Self" : CARD_VALUES[Math.max(0, card.getSelectedItemPosition())]);
            } catch (Exception ignored) {}
            Phone.send(this, e);
            Toast.makeText(this, "Added " + s + ". It goes into Money when the Tracker next opens.", Toast.LENGTH_SHORT).show();
            return true;
        }, "Cancel");
    }

    private static String currentDir(RadioGroup g) {
        View v = g.findViewById(g.getCheckedRadioButtonId());
        return v == null ? "out" : String.valueOf(v.getTag());
    }

    private static List<String> list(JSONObject m, String key, List<String> fallback) {
        JSONArray a = m != null ? m.optJSONArray(key) : null;
        if (a == null || a.length() == 0) return fallback;
        List<String> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
        return out;
    }

    // ---------- layout helpers ----------

    interface Submit { boolean run(); }

    private void buttons(String ok, Submit go, String cancel) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.END);
        if (cancel != null) {
            Button c = new Button(this, null, android.R.attr.borderlessButtonStyle);
            c.setText(cancel);
            c.setOnClickListener(v -> finish());
            row.addView(c);
        }
        Button b = new Button(this, null, android.R.attr.borderlessButtonStyle);
        b.setText(ok);
        b.setOnClickListener(v -> {
            if (go == null || go.run()) {
                Widgets.updateAll(this);
                finish();
            }
        });
        row.addView(b);
        LinearLayout.LayoutParams lp = full();
        lp.topMargin = dp(6);
        box.addView(row, lp);
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams lp = full();
        lp.topMargin = dp(6);
        box.addView(t, lp);
        return t;
    }

    private void note(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setAlpha(0.75f);
        LinearLayout.LayoutParams lp = full();
        lp.topMargin = dp(6);
        box.addView(t, lp);
    }

    private LinearLayout.LayoutParams full() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int x) {
        return Math.round(x * getResources().getDisplayMetrics().density);
    }
}
