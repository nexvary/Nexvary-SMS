package com.nexvary.sms;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {

    // ── Palette ──────────────────────────────────────────────────────────────
    private static final int C_BG      = 0xFF0A0A12;
    private static final int C_CARD    = 0xFF131320;
    private static final int C_BORDER  = 0xFF2C2C44;
    private static final int C_BRAND   = 0xFFC9A227;  // gold
    private static final int C_TEAL    = 0xFF12C3AA;
    private static final int C_TEXT    = 0xFFE8E8F0;
    private static final int C_DIM     = 0xFF777799;
    private static final int C_WARN    = 0xFFE8A020;
    private static final int C_ERROR   = 0xFFE04040;
    private static final int C_SUCCESS = 0xFF20B060;
    private static final int C_FIELD   = 0xFF1A1A28;

    // ── Statics shared across rotations ─────────────────────────────────────
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor();

    // ── Instance state ───────────────────────────────────────────────────────
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        public void run() { render(); handler.postDelayed(this, 1000); }
    };

    private EditText keyField, toField, messageField, messageIdField;
    private TextView resultView, segmentsView, keyStatusView;
    private Button sendButton, checkButton;
    private LinearLayout rootLayout;
    private KeyVault vault;
    private SharedPreferences journal;
    private boolean vaultHasKey = false;

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);

        journal = getSharedPreferences("journal", MODE_PRIVATE);
        vault = new KeyVault(this);
        try { vaultHasKey = !vault.read().isEmpty(); } catch (Exception ignored) {}

        // Root scroll
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(C_BG);

        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(dp(16), dp(28), dp(16), dp(32));
        rootLayout.setBackgroundColor(C_BG);
        rootLayout.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(rootLayout);
        setContentView(scroll);

        buildHeader();
        buildApiKeySection();
        buildComposeSection();
        buildSendButton();
        buildResultSection();
        buildQuerySection();
        buildBottomActions();
        buildDisclaimer();

        // Process-death recovery: in-flight request has unknown outcome.
        if (!RUNNING.get() && journal.getBoolean("inflight", false)) {
            journal.edit().putBoolean("inflight", false)
                    .putString("display",
                            "نتيجة غير مؤكدة — انقطع التطبيق أثناء الإرسال.\n"
                            + "راجع سجل Telnyx قبل أي إعادة إرسال.")
                    .commit();
        }
        render();
    }

    @Override protected void onResume() { super.onResume(); handler.post(tick); }
    @Override protected void onPause()  { handler.removeCallbacks(tick); super.onPause(); }

    // ── UI builders ──────────────────────────────────────────────────────────

    private void buildHeader() {
        LinearLayout hdr = new LinearLayout(this);
        hdr.setOrientation(LinearLayout.VERTICAL);
        hdr.setPadding(dp(20), dp(20), dp(20), dp(20));
        hdr.setGravity(Gravity.CENTER);

        // Gold accent bar
        View bar = new View(this);
        bar.setBackgroundColor(C_BRAND);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dp(60), dp(3));
        barLp.gravity = Gravity.CENTER_HORIZONTAL;
        barLp.setMargins(0, 0, 0, dp(14));
        hdr.addView(bar, barLp);

        TextView brand = tv("NEXVARY", 32, C_BRAND);
        brand.setGravity(Gravity.CENTER);
        brand.setLetterSpacing(0.2f);
        hdr.addView(brand);

        TextView sub = tv("SMS • Nexvary Inc • Telnyx", 13, C_DIM);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(4), 0, dp(2));
        hdr.addView(sub);

        rootLayout.addView(hdr, mlp(0, 0, 0, dp(12)));
    }

    private void buildApiKeySection() {
        rootLayout.addView(sectionLabel("مفتاح API"));

        LinearLayout card = card(C_CARD, C_BORDER, dp(10));

        keyStatusView = new TextView(this);
        keyStatusView.setTextSize(12);
        keyStatusView.setPadding(dp(2), 0, dp(2), dp(8));
        refreshKeyStatus();
        card.addView(keyStatusView);

        keyField = secureField("أدخل مفتاح Telnyx API");
        card.addView(keyField, mlp(0, 0, 0, dp(8)));

        LinearLayout row = hRow();
        Button saveBtn = chip("حفظ مشفّر", C_TEAL, 0xFF061A18);
        Button forgetBtn = chip("نسيان", C_ERROR, 0xFF1A0808);
        saveBtn.setOnClickListener(v -> saveKey());
        forgetBtn.setOnClickListener(v -> confirmForgetKey());
        row.addView(saveBtn, weightLp(dp(6)));
        row.addView(forgetBtn, weightLp(0));
        card.addView(row, mlp(0, 0, 0, 0));

        rootLayout.addView(card, mlp(0, 0, 0, dp(16)));
    }

    private void buildComposeSection() {
        rootLayout.addView(sectionLabel("إرسال رسالة"));

        LinearLayout card = card(C_CARD, C_BORDER, dp(10));

        card.addView(fieldLabel("رقم المستلم  (مصر: +201…)"));
        toField = plainField("+201065946452");
        toField.setInputType(InputType.TYPE_CLASS_PHONE);
        toField.setTextDirection(View.TEXT_DIRECTION_LTR);
        card.addView(toField, mlp(0, 0, 0, dp(10)));

        card.addView(fieldLabel("نص الرسالة"));
        messageField = plainField("اكتب رسالتك هنا…");
        messageField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        messageField.setMinLines(4);
        card.addView(messageField, mlp(0, 0, 0, dp(6)));

        segmentsView = new TextView(this);
        segmentsView.setTextSize(12);
        segmentsView.setTextColor(C_DIM);
        card.addView(segmentsView);
        updateParts();

        messageField.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            public void onTextChanged(CharSequence s, int i, int b, int c) { updateParts(); }
            public void afterTextChanged(Editable e) {}
        });

        rootLayout.addView(card, mlp(0, 0, 0, dp(8)));

        // Cost notice
        LinearLayout warn = card(0xFF18120A, 0xFF5C3E00, dp(8));
        warn.addView(tv("تنبيه: الإرسال مدفوع. الرسائل العربية UCS-2 وتستهلك أجزاء أكثر. السعر الفعلي وفق حسابك وشبكة المستلم.", 12, C_WARN));
        rootLayout.addView(warn, mlp(0, 0, 0, dp(16)));
    }

    private void buildSendButton() {
        sendButton = new Button(this);
        sendButton.setText("مراجعة وإرسال ← طلب واحد فقط");
        sendButton.setTextSize(16);
        sendButton.setTextColor(Color.BLACK);
        sendButton.setAllCaps(false);
        sendButton.setBackground(roundRect(C_BRAND, 0, dp(10)));
        sendButton.setPadding(dp(16), dp(14), dp(16), dp(14));
        sendButton.setOnClickListener(v -> review());
        rootLayout.addView(sendButton, mlp(0, 0, 0, dp(16)));
    }

    private void buildResultSection() {
        rootLayout.addView(sectionLabel("نتيجة آخر محاولة"));

        LinearLayout card = card(C_CARD, C_BORDER, dp(10));
        resultView = new TextView(this);
        resultView.setTextSize(14);
        resultView.setTextColor(C_TEXT);
        resultView.setTextIsSelectable(true);
        resultView.setLineSpacing(dp(3), 1f);
        card.addView(resultView);
        rootLayout.addView(card, mlp(0, 0, 0, dp(16)));
    }

    private void buildQuerySection() {
        rootLayout.addView(sectionLabel("فحص حالة الرسالة (GET فقط)"));

        LinearLayout card = card(C_CARD, C_BORDER, dp(10));
        messageIdField = plainField("UUID معرّف الرسالة");
        messageIdField.setInputType(InputType.TYPE_CLASS_TEXT);
        messageIdField.setTextDirection(View.TEXT_DIRECTION_LTR);
        messageIdField.setText(journal.getString("id", ""));
        card.addView(messageIdField, mlp(0, 0, 0, dp(8)));

        checkButton = chip("تحديث الحالة ← بدون إرسال", C_TEAL, 0xFF06181A);
        checkButton.setOnClickListener(v -> checkStatus());
        card.addView(checkButton, mlp(0, 0, 0, 0));

        rootLayout.addView(card, mlp(0, 0, 0, dp(16)));
    }

    private void buildBottomActions() {
        LinearLayout row = hRow();

        Button newBtn = chip("رسالة جديدة", C_TEXT, C_CARD);
        Button portalBtn = chip("سجل Telnyx ↗", C_DIM, C_CARD);
        newBtn.setOnClickListener(v -> confirmNewMessage());
        portalBtn.setOnClickListener(v -> startActivity(
                new Intent(Intent.ACTION_VIEW, Uri.parse("https://portal.telnyx.com/"))));

        row.addView(newBtn, weightLp(dp(6)));
        row.addView(portalBtn, weightLp(0));
        rootLayout.addView(row, mlp(0, 0, 0, dp(16)));
    }

    private void buildDisclaimer() {
        TextView d = tv(
                "Accepted / Sent ≠ Delivered — اسم المرسل الظاهر يؤكده المستلم."
                + " لا صلاحيات SMS أو جهات اتصال. لا يُحفظ نص الرسالة.",
                11, C_DIM);
        d.setGravity(Gravity.CENTER);
        rootLayout.addView(d, mlp(dp(8), 0, dp(8), dp(8)));
    }

    // ── Action handlers ──────────────────────────────────────────────────────

    private void saveKey() {
        String k = keyField.getText().toString().trim();
        if (k.isEmpty()) { toast("أدخل المفتاح في الحقل أولًا."); return; }
        try {
            vault.save(k);
            keyField.setText("");
            vaultHasKey = true;
            refreshKeyStatus();
            toast("حُفظ مشفّرًا في Android Keystore.");
        } catch (Exception e) {
            toast("فشل الحفظ. تحقق من قفل الجهاز وحاول مجددًا.");
        }
    }

    private void confirmForgetKey() {
        new AlertDialog.Builder(this)
                .setTitle("حذف المفتاح المحفوظ")
                .setMessage("سيُحذف المفتاح من Android Keystore على هذا الجهاز نهائيًا.")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حذف", (d, w) -> {
                    try {
                        vault.forget();
                        keyField.setText("");
                        vaultHasKey = false;
                        refreshKeyStatus();
                        toast("تم حذف المفتاح.");
                    } catch (Exception e) {
                        toast("فشل الحذف: " + e.getMessage());
                    }
                }).show();
    }

    private void refreshKeyStatus() {
        if (keyStatusView == null) return;
        if (vaultHasKey) {
            keyStatusView.setText("🔒 مفتاح محفوظ ومشفّر — اترك الحقل فارغًا لاستخدامه");
            keyStatusView.setTextColor(C_TEAL);
        } else {
            keyStatusView.setText("لا مفتاح محفوظ — أدخله للاستخدام الفوري أو احفظه");
            keyStatusView.setTextColor(C_DIM);
        }
    }

    private String validKey() throws Exception {
        String k = keyField.getText().toString().trim();
        if (k.isEmpty()) k = vault.read();
        if (k.isEmpty() || k.contains("\n") || k.contains("\r"))
            throw new IllegalArgumentException("أدخل مفتاح Telnyx API أو احفظه أولًا.");
        return k;
    }

    private void review() {
        if (journal.getBoolean("locked", false) || journal.getBoolean("inflight", false)) return;
        try {
            final String recipient = SmsPolicy.normalize(toField.getText().toString());
            final String text      = messageField.getText().toString();
            SmsPolicy.validateText(text);
            validKey();
            toField.setText(recipient);
            int parts = SmsPolicy.parts(text);
            new AlertDialog.Builder(this)
                    .setTitle("تأكيد إرسال مدفوع")
                    .setMessage("المرسل: Nexvary Inc\nالمستلم: " + recipient
                            + "\nالأجزاء التقديرية: " + parts
                            + "\n\n" + text
                            + "\n\nإرسال طلب واحد فقط — بدون إعادة تلقائية؟")
                    .setNegativeButton("إلغاء", null)
                    .setPositiveButton("أوافق — أرسل الآن", (d, w) -> sendOnce(recipient, text))
                    .show();
        } catch (Exception e) {
            toast(e instanceof IllegalArgumentException ? e.getMessage() : "أدخل مفتاح API صالحًا.");
        }
    }

    private void sendOnce(String recipient, String text) {
        if (journal.getBoolean("locked", false) || journal.getBoolean("inflight", false)) return;
        final String token;
        try { token = validKey(); } catch (Exception e) { toast("أدخل مفتاح API صالحًا."); return; }
        if (!RUNNING.compareAndSet(false, true)) return;
        boolean persisted = journal.edit()
                .putBoolean("locked", true).putBoolean("inflight", true).remove("id")
                .putString("display", "⏳ جارٍ الإرسال… إن انقطع التطبيق راجع سجل Telnyx.")
                .commit();
        if (!persisted) { RUNNING.set(false); toast("تعذّر تسجيل المحاولة؛ لم تُرسل."); return; }
        keyField.setText("");
        messageIdField.setText("");
        render();
        NETWORK.execute(() -> {
            try { record(TelnyxClient.send(token, recipient, text)); }
            catch (Exception e) {
                journal.edit().putString("display",
                        "النتيجة غير مؤكدة. انقطاع أو استجابة غير صالحة.\n"
                        + "لا تعِد الإرسال؛ افحص سجل Telnyx أولًا.").commit();
            } finally {
                journal.edit().putBoolean("inflight", false).commit();
                RUNNING.set(false);
            }
        });
    }

    private void checkStatus() {
        if (journal.getBoolean("inflight", false)) return;
        try {
            final String token = validKey();
            final String id    = SmsPolicy.messageId(messageIdField.getText().toString());
            if (!RUNNING.compareAndSet(false, true)) return;
            if (!journal.edit().putBoolean("inflight", true).commit()) { RUNNING.set(false); return; }
            render();
            NETWORK.execute(() -> {
                try {
                    TelnyxClient.Result r = TelnyxClient.status(token, id);
                    if (!r.error.isEmpty())
                        journal.edit().putString("display",
                                "تعذّر الاستعلام: " + r.error + "\nلم تُرسل رسالة جديدة.").commit();
                    else record(r);
                } catch (Exception e) {
                    journal.edit().putString("display",
                            "تعذّر تحديث الحالة. لم تُرسل رسالة جديدة.").commit();
                } finally {
                    journal.edit().putBoolean("inflight", false).commit();
                    RUNNING.set(false);
                }
            });
        } catch (Exception e) {
            toast("أدخل مفتاح API ومعرّف رسالة صالحين.");
        }
    }

    private void confirmNewMessage() {
        new AlertDialog.Builder(this)
                .setTitle("بدء رسالة جديدة")
                .setMessage("إذا كانت نتيجة الإرسال السابق غير واضحة، افتح سجل Telnyx "
                        + "وتأكد من عدم وصول الرسالة قبل المتابعة.")
                .setNegativeButton("رجوع", null)
                .setPositiveButton("راجعت السجل — متابعة", (d, w) -> {
                    if (RUNNING.get() || journal.getBoolean("inflight", false)) {
                        toast("انتظر انتهاء الطلب الحالي.");
                        return;
                    }
                    journal.edit().putBoolean("locked", false).commit();
                    messageField.setText("");
                    render();
                }).show();
    }

    private void record(TelnyxClient.Result r) {
        String display = r.error.isEmpty()
                ? "ID: " + r.id + "\n" + statusLabel(r.status) + "\nالتكلفة: " + r.cost
                : r.error;
        SharedPreferences.Editor e = journal.edit().putString("display", display);
        if (!r.id.isEmpty()) e.putString("id", r.id);
        e.commit();
    }

    private static String statusLabel(String s) {
        switch (s) {
            case "delivered": return "الحالة: delivered — أكدت Telnyx الوصول.";
            case "sent":      return "الحالة: sent — في الطريق إلى الهاتف.";
            case "accepted":  return "الحالة: accepted — قبلتها Telnyx، لم تصل بعد.";
            case "failed":    return "الحالة: failed — فشل الإرسال. راجع سجل Telnyx.";
            default:          return "الحالة: " + s;
        }
    }

    private void render() {
        if (sendButton == null) return;
        boolean inflight = RUNNING.get() || journal.getBoolean("inflight", false);
        boolean locked   = journal.getBoolean("locked", false);
        sendButton.setEnabled(!inflight && !locked);
        sendButton.setAlpha(!inflight && !locked ? 1f : 0.35f);
        checkButton.setEnabled(!inflight);
        checkButton.setAlpha(!inflight ? 1f : 0.35f);
        resultView.setText(journal.getString("display", "لا توجد محاولة إرسال بعد."));
        String id = journal.getString("id", "");
        if (!id.isEmpty() && messageIdField.getText().length() == 0) messageIdField.setText(id);
    }

    private void updateParts() {
        if (messageField == null || segmentsView == null) return;
        String t = messageField.getText().toString();
        segmentsView.setText("أحرف: " + t.length() + "  ·  أجزاء تقديرية: " + SmsPolicy.parts(t));
    }

    // ── View factory helpers ─────────────────────────────────────────────────

    private LinearLayout card(int bg, int border, int radius) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        if (border != 0) c.setBackground(roundRect(bg, border, radius));
        else             c.setBackgroundColor(bg);
        return c;
    }

    private LinearLayout hRow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    private TextView sectionLabel(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(12);
        v.setTextColor(C_DIM);
        v.setPadding(dp(2), 0, dp(2), dp(6));
        return v;
    }

    private TextView fieldLabel(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(12);
        v.setTextColor(C_DIM);
        v.setPadding(dp(2), 0, dp(2), dp(4));
        return v;
    }

    private EditText secureField(String hint) {
        EditText v = new EditText(this);
        v.setHint(hint);
        v.setHintTextColor(0xFF555570);
        v.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        v.setTextSize(15);
        v.setTextColor(C_TEXT);
        v.setBackground(roundRect(C_FIELD, C_BORDER, dp(6)));
        v.setPadding(dp(12), dp(10), dp(12), dp(10));
        v.setSaveEnabled(false);
        v.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        v.setTextDirection(View.TEXT_DIRECTION_LTR);
        return v;
    }

    private EditText plainField(String hint) {
        EditText v = new EditText(this);
        v.setHint(hint);
        v.setHintTextColor(0xFF555570);
        v.setTextSize(15);
        v.setTextColor(C_TEXT);
        v.setBackground(roundRect(C_FIELD, C_BORDER, dp(6)));
        v.setPadding(dp(12), dp(10), dp(12), dp(10));
        return v;
    }

    private Button chip(String text, int textColor, int bgColor) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(14);
        b.setTextColor(textColor);
        b.setAllCaps(false);
        b.setBackground(roundRect(bgColor, 0, dp(8)));
        b.setPadding(dp(10), dp(10), dp(10), dp(10));
        return b;
    }

    private TextView tv(String text, int size, int color) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color);
        return v;
    }

    private GradientDrawable roundRect(int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(radius);
        d.setColor(fill);
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }

    private LinearLayout.LayoutParams mlp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(l, t, r, b);
        return lp;
    }

    private LinearLayout.LayoutParams weightLp(int endMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(0, 0, endMargin, 0);
        return lp;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
