package com.nexvary.sms;
import org.junit.Test;
import static org.junit.Assert.*;
public class SmsPolicyTest {
    @Test public void normalizesEgyptMobile() { assertEquals("+201065946452", SmsPolicy.normalize("+20 10-6594(6452)")); }
    @Test public void normalizesNonBreakingSpaces() { assertEquals("+201065946452", SmsPolicy.normalize("+20\u00a01065946452")); }
    @Test public void supportsAllEgyptPrefixes() { for (String p : new String[]{"10","11","12","15"}) assertEquals("+20"+p+"12345678",SmsPolicy.normalize("+20"+p+"12345678")); }
    @Test(expected=IllegalArgumentException.class) public void rejectsForeignNumber() { SmsPolicy.normalize("+13322691151"); }
    @Test(expected=IllegalArgumentException.class) public void rejectsLandline() { SmsPolicy.normalize("+20212345678"); }
    @Test(expected=IllegalArgumentException.class) public void rejectsLocalFormat() { SmsPolicy.normalize("01065946452"); }
    @Test(expected=IllegalArgumentException.class) public void rejectsUriInjection() { SmsPolicy.normalize("+201065946452?x=1"); }
    @Test(expected=IllegalArgumentException.class) public void rejectsEmptyText() { SmsPolicy.validateText(" \n"); }
    @Test(expected=IllegalArgumentException.class) public void rejectsTooLongText() { SmsPolicy.validateText("a".repeat(1601)); }
    @Test public void gsmMultipartBoundary() { assertEquals(1,SmsPolicy.parts("a".repeat(160))); assertEquals(2,SmsPolicy.parts("a".repeat(161))); }
    @Test public void unicodeMultipartBoundary() { assertEquals(1,SmsPolicy.parts("س".repeat(70))); assertEquals(2,SmsPolicy.parts("س".repeat(71))); }
    @Test public void extensionCharactersCountTwice() { assertEquals(1,SmsPolicy.parts("^".repeat(80))); assertEquals(2,SmsPolicy.parts("^".repeat(81))); }
    @Test public void emojiUsesTwoUtf16Units() { assertEquals(1,SmsPolicy.parts("😀".repeat(35))); assertEquals(2,SmsPolicy.parts("😀".repeat(36))); }
    @Test public void validatesId() { assertEquals("4001a1ea-906a-4085-b6a2-9188edac65b8",SmsPolicy.messageId("4001a1ea-906a-4085-b6a2-9188edac65b8")); }
    @Test(expected=IllegalArgumentException.class) public void rejectsIdPathTraversal() { SmsPolicy.messageId("../messages"); }
}
