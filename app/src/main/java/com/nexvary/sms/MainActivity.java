package com.nexvary.sms;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.Editable;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final java.util.concurrent.atomic.AtomicBoolean RUNNING = new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private EditText key, to, message, messageId;
    private TextView result, segments;
    private Button send, check;
    private KeyVault vault;
    private SharedPreferences journal;
    private LinearLayout layout;
    private final Runnable refresh = new Runnable() { public void run() { render(); handler.postDelayed(this, 1000); } };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        journal = getSharedPreferences("journal", MODE_PRIVATE); vault = new KeyVault(this);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(dp(22), dp(36), dp(22), dp(36));
        layout.setBackgroundColor(Color.rgb(243,247,250)); layout.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(layout); setContentView(scroll);
        label("NEXVARY", 30, "#10344A"); label("رسائل شركتك، بخطوات بسيطة", 21, "#10344A");
        label("المرسل المعتمد: Nexvary Inc\nمصر • إرسال عبر Telnyx والإنترنت", 15, "#486174");
        key = field("مفتاح Telnyx API", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setSaveEnabled(false); key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        key.setTextDirection(View.TEXT_DIRECTION_LTR);
        try { key.setText(vault.read()); } catch (Exception e) { toast("تعذّر فتح المفتاح المحفوظ. أدخله مجددًا."); }
        button("حفظ المفتاح مشفّرًا على هذا الجهاز", () -> {
            try { vault.save(validKey()); key.setText(""); toast("حُفظ مشفّرًا. اترك الحقل فارغًا لاستخدامه."); }
            catch (Exception e) { toast("تعذّر حفظ المفتاح. تحقق من إدخاله وقفل الجهاز."); }
        });
        button("نسيان المفتاح", () -> { try { vault.forget(); key.setText(""); toast("تم حذف المفتاح المحفوظ."); } catch (Exception e) { toast("تعذّر حذف المفتاح."); } });
        to = field("رقم المحمول المصري: +2010…", InputType.TYPE_CLASS_PHONE); to.setTextDirection(View.TEXT_DIRECTION_LTR);
        message = field("اكتب رسالتك هنا", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); message.setMinLines(4);
        segments = label("", 14, "#486174");
        message.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) { }
            public void onTextChanged(CharSequence s,int start,int before,int count) { updateParts(); }
            public void afterTextChanged(Editable e) { }
        }); updateParts();
        label("الإرسال مدفوع. العربية والرسائل الطويلة قد تُحسب عدة أجزاء. السعر النهائي بحسب حسابك والشبكة؛ لا يُعرض سعر ثابت غير متحقق منه.", 14, "#875619");
        send = button("مراجعة وإرسال رسالة واحدة", this::review);
        label("نتيجة آخر محاولة", 20, "#10344A"); result = label("", 15, "#10344A"); result.setTextIsSelectable(true);
        messageId = field("معرّف رسالة لفحص حالتها (UUID)", InputType.TYPE_CLASS_TEXT); messageId.setTextDirection(View.TEXT_DIRECTION_LTR);
        messageId.setText(journal.getString("id", ""));
        check = button("تحديث حالة التسليم — دون إعادة إرسال", this::checkStatus);
        button("بدء رسالة جديدة / فك الحظر بعد مراجعة السجل", () -> new AlertDialog.Builder(this)
            .setTitle("تحقق من السجل أولًا")
            .setMessage("إذا كانت النتيجة غير واضحة، افتح سجل Telnyx وتأكد من عدم إرسال الرسالة قبل المتابعة. فك الحظر لا يلغي رسالة سبق قبولها.")
            .setNegativeButton("رجوع", null).setPositiveButton("راجعت السجل — متابعة", (d,w) -> {
                if (RUNNING.get() || journal.getBoolean("inflight", false)) { toast("انتظر انتهاء الطلب الحالي."); return; }
                journal.edit().putBoolean("locked", false).commit(); message.setText(""); render();
            }).show());
        button("فتح سجل Telnyx", () -> startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://portal.telnyx.com/"))));
        label("Accepted أو Sent لا تعني Delivered. اسم المرسل الظاهر على الهاتف يحتاج تأكيد المستلم. لا يُحفظ نص الرسالة في سجل التطبيق، ولا تُطلب صلاحيات SMS أو جهات الاتصال.", 13, "#486174");
        // After process death an in-flight request has an unknown outcome: never replay it.
        if (!RUNNING.get() && journal.getBoolean("inflight", false)) {
            journal.edit().putBoolean("inflight", false).putString("display", "نتيجة محاولة سابقة غير مؤكدة. راجع سجل Telnyx؛ لا تعِد الإرسال تلقائيًا.").commit();
        }
        render();
    }
    private String validKey() throws Exception {
        String k = key.getText().toString().trim(); if (k.isEmpty()) k = vault.read();
        if (k.isEmpty() || k.contains("\n") || k.contains("\r")) throw new IllegalArgumentException("أدخل مفتاح API صالحًا أو احفظه أولًا.");
        return k;
    }
    private void review() {
        if (journal.getBoolean("locked", false) || journal.getBoolean("inflight", false)) return;
        try {
            final String recipient = SmsPolicy.normalize(to.getText().toString());
            final String text = message.getText().toString(); SmsPolicy.validateText(text); validKey();
            to.setText(recipient);
            new AlertDialog.Builder(this).setTitle("تأكيد إرسال مدفوع")
                .setMessage("المرسل: Nexvary Inc\nالمستلم: " + recipient + "\nالأجزاء التقديرية: " + SmsPolicy.parts(text) + "\n\n" + text + "\n\nإرسال طلب واحد فقط، دون إعادة محاولة تلقائية؟")
                .setNegativeButton("إلغاء", null).setPositiveButton("أوافق — أرسل مرة واحدة", (d,w) -> sendOnce(recipient,text)).show();
        } catch (Exception e) { toast(e instanceof IllegalArgumentException ? e.getMessage() : "أدخل مفتاح API صالحًا."); }
    }
    private void sendOnce(String recipient, String text) {
        if (journal.getBoolean("locked", false) || journal.getBoolean("inflight", false)) return;
        final String token;
        try { token = validKey(); } catch (Exception e) { toast("أدخل مفتاح API صالحًا."); return; }
        if (!RUNNING.compareAndSet(false,true)) return;
        if (!journal.edit().putBoolean("locked",true).putBoolean("inflight",true).remove("id")
                .putString("display","جارٍ الإرسال… إن انقطع التطبيق فراجع سجل Telnyx قبل التكرار.").commit()) { RUNNING.set(false); toast("تعذّر تسجيل المحاولة؛ لم تُرسل."); return; }
        key.setText(""); messageId.setText(""); render();
        NETWORK.execute(() -> {
            try { record(TelnyxClient.send(token,recipient,text)); }
            catch (Exception e) { journal.edit().putString("display","النتيجة غير مؤكدة بسبب انقطاع أو استجابة غير صالحة. لا تعِد الإرسال؛ افحص سجل Telnyx.").commit(); }
            finally { journal.edit().putBoolean("inflight",false).commit(); RUNNING.set(false); }
        });
    }
    private void checkStatus() {
        if (journal.getBoolean("inflight",false)) return;
        try {
            final String token = validKey(); final String id = SmsPolicy.messageId(messageId.getText().toString());
            if (!RUNNING.compareAndSet(false,true)) return;
            if (!journal.edit().putBoolean("inflight",true).commit()) { RUNNING.set(false); return; } render();
            NETWORK.execute(() -> {
                try {
                    TelnyxClient.Result response = TelnyxClient.status(token,id);
                    if (!response.error.isEmpty()) journal.edit().putString("display", "تعذر الاستعلام: " + response.error + "\nلم تُرسل رسالة جديدة.").commit();
                    else record(response);
                } catch (Exception e) { journal.edit().putString("display","تعذّر تحديث الحالة. لم تُرسل رسالة جديدة. جرّب الاستعلام لاحقًا.").commit(); }
                finally { journal.edit().putBoolean("inflight",false).commit(); RUNNING.set(false); }
            });
        } catch (Exception e) { toast("أدخل مفتاح API ومعرّف رسالة صالحين."); }
    }
    private void record(TelnyxClient.Result r) {
        String display = r.error.isEmpty() ? "Message ID: " + r.id + "\nالحالة: " + r.status + "\nالتكلفة المبلّغ عنها: " + r.cost
            + ("delivered".equals(r.status) ? "\nأكدت Telnyx التسليم. تحقق من الاسم على هاتف المستلم." : "\nلم يُثبت وصول الرسالة إلى الهاتف بهذه الحالة.") : r.error;
        SharedPreferences.Editor edit = journal.edit().putString("display",display); if (!r.id.isEmpty()) edit.putString("id",r.id); edit.commit();
    }
    private void render() {
        if (send == null) return;
        boolean busy = RUNNING.get() || journal.getBoolean("inflight",false);
        send.setEnabled(!busy && !journal.getBoolean("locked",false)); check.setEnabled(!busy);
        result.setText(journal.getString("display","لا توجد محاولة إرسال بعد."));
        String id = journal.getString("id",""); if (!id.isEmpty() && messageId.getText().length() == 0) messageId.setText(id);
    }
    private void updateParts() { segments.setText("الأحرف: " + message.getText().length() + "  •  أجزاء SMS تقديرية: " + SmsPolicy.parts(message.getText().toString())); }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density); }
    private TextView label(String s,int size,String color) { TextView v=new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(Color.parseColor(color)); v.setPadding(0,dp(9),0,dp(9)); layout.addView(v); return v; }
    private EditText field(String hint,int type) { EditText v=new EditText(this); v.setHint(hint); v.setInputType(type); v.setTextSize(16); v.setPadding(dp(12),dp(12),dp(12),dp(12)); layout.addView(v,new LinearLayout.LayoutParams(-1,-2)); return v; }
    private Button button(String title,Runnable action) { Button b=new Button(this); b.setText(title); b.setAllCaps(false); b.setOnClickListener(v -> action.run()); layout.addView(b,new LinearLayout.LayoutParams(-1,-2)); return b; }
    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
}
