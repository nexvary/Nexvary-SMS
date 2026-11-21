package com.nexvary.sms;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import java.nio.charset.StandardCharsets;


final class TelnyxClient {
    // Overrideable in same-package unit tests only; never log or expose at runtime.
    static String BASE_URL = "https://api.telnyx.com/v2/messages";
    static final class Result {
        final String id, status, cost, error;
        Result(String id, String status, String cost, String error) { this.id=id; this.status=status; this.cost=cost; this.error=error; }
    }
    static Result send(String key, String to, String text) throws Exception {
        JSONObject body = new JSONObject().put("from", SmsPolicy.SENDER).put("to", SmsPolicy.normalize(to))
            .put("text", text).put("messaging_profile_id", SmsPolicy.PROFILE);
        return request(key, "POST", "", body);
    }
    static Result status(String key, String id) throws Exception { return request(key, "GET", "/" + SmsPolicy.messageId(id), null); }
    private static Result request(String key, String method, String suffix, JSONObject body) throws Exception {
        // Explicitly disable transport retries: an ambiguous paid POST must never replay.
        OkHttpClient client = new OkHttpClient.Builder().retryOnConnectionFailure(false)
            .followRedirects(false).followSslRedirects(false).connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS).build();
        Request.Builder request = new Request.Builder().url(BASE_URL + suffix)
            .header("Authorization", "Bearer " + key).header("Accept", "application/json");
        if (body != null) request.post(RequestBody.create(body.toString().getBytes(StandardCharsets.UTF_8), MediaType.get("application/json; charset=utf-8")));
        else request.get();
        try (Response response = client.newCall(request.build()).execute()) {
            if (!response.isSuccessful()) return new Result("", "unknown", "", "HTTP " + response.code() + " — راجع سجل Telnyx قبل أي إعادة إرسال.");
            if (response.body() == null) throw new Exception("Empty response");
            String raw;
            try (InputStream in = response.body().byteStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = in.read(buffer)) != -1) { if (out.size() + count > 262144) throw new Exception("Response too large"); out.write(buffer, 0, count); }
                raw = out.toString(StandardCharsets.UTF_8.name());
            }
            JSONObject data = new JSONObject(raw).getJSONObject("data");
            String id = SmsPolicy.messageId(data.getString("id"));
            JSONArray recipients = data.optJSONArray("to");
            String status = recipients != null && recipients.length() > 0 ? recipients.getJSONObject(0).optString("status", "accepted") : "accepted";
            JSONObject price = data.optJSONObject("cost");
            String cost = price == null ? "غير متاحة بعد" : price.optString("amount", "؟") + " " + price.optString("currency", "USD");
            return new Result(id, status, cost, "");
        }
    }
}
