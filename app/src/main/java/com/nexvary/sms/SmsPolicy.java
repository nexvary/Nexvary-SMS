package com.nexvary.sms;

import java.util.UUID;

public final class SmsPolicy {
    public static final String SENDER = "Nexvary Inc";
    public static final String PROFILE = "4001a1ea-906a-4085-b6a2-9188edac65b8";
    private static final String GSM = "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà";
    private static final String EXT = "\f^{}\\[~]|€";
    private SmsPolicy() { }
    public static String normalize(String number) {
        String n = number == null ? "" : number.replaceAll("[\\s\\p{Z}()\\-]", "");
        if (!n.matches("\\+201[0125][0-9]{8}")) throw new IllegalArgumentException("أدخل رقم محمول مصري: +2010 أو +2011 أو +2012 أو +2015 ثم 8 أرقام.");
        return n;
    }
    public static void validateText(String text) {
        if (text == null || text.trim().isEmpty()) throw new IllegalArgumentException("اكتب نص الرسالة أولًا.");
        if (text.length() > 1600) throw new IllegalArgumentException("الحد في التطبيق 1600 وحدة نصية لتجنب رسائل طويلة مكلفة.");
    }
    public static int parts(String text) {
        int units = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (GSM.indexOf(c) >= 0) units++;
            else if (EXT.indexOf(c) >= 0) units += 2;
            else return text.length() <= 70 ? 1 : (text.length() + 66) / 67;
        }
        return units <= 160 ? 1 : (units + 152) / 153;
    }
    public static String messageId(String value) {
        String id = value.trim();
        if (!UUID.fromString(id).toString().equalsIgnoreCase(id)) throw new IllegalArgumentException("معرّف الرسالة يجب أن يكون UUID صالحًا.");
        return id;
    }
}
