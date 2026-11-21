package com.nexvary.sms;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class TelnyxClientTest {
    private static final String VALID_ID = "4001a1ea-906a-4085-b6a2-9188edac65b8";
    private static final String TO = "+201065946452";
    private static final String KEY = "test-key-not-real";
    private static final String SEND_OK =
        "{\"data\":{\"id\":\"" + VALID_ID + "\",\"to\":[{\"status\":\"accepted\"}],\"cost\":null}}";

    private MockWebServer server;

    @Before public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        TelnyxClient.BASE_URL = server.url("/v2/messages").toString();
    }

    @After public void tearDown() throws IOException {
        TelnyxClient.BASE_URL = "https://api.telnyx.com/v2/messages";
        server.shutdown();
    }

    // UTF-8: Arabic text must arrive at server ungarbled, Content-Type must declare charset.
    @Test public void sendBodyIsUtf8() throws Exception {
        server.enqueue(new MockResponse().setBody(SEND_OK));
        String arabic = "مرحباً بك في نظام Nexvary";
        TelnyxClient.Result r = TelnyxClient.send(KEY, TO, arabic);
        assertTrue("send should succeed", r.error.isEmpty());
        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(req);
        String body = req.getBody().readString(StandardCharsets.UTF_8);
        assertTrue("Arabic text must be in body verbatim", body.contains(arabic));
        String ct = req.getHeader("Content-Type");
        assertNotNull(ct);
        assertTrue("Content-Type must declare utf-8", ct.toLowerCase().contains("utf-8"));
    }

    // HTTP 422: should return an error Result with the status code, never throw.
    @Test public void http422ReturnsErrorResult() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(422).setBody("{\"errors\":[]}"));
        TelnyxClient.Result r = TelnyxClient.send(KEY, TO, "test");
        assertFalse("error field must be non-empty", r.error.isEmpty());
        assertTrue("error must mention 422", r.error.contains("422"));
        assertEquals("id must be empty on error", "", r.id);
    }

    // HTTP 500: same contract as 422.
    @Test public void http500ReturnsErrorResult() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500));
        TelnyxClient.Result r = TelnyxClient.send(KEY, TO, "test");
        assertFalse(r.error.isEmpty());
        assertTrue(r.error.contains("500"));
    }

    // Invalid JSON on 200: must throw, not silently return a partial result.
    @Test(expected = Exception.class)
    public void invalidJsonThrows() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("not json {{{{"));
        TelnyxClient.send(KEY, TO, "test");
    }

    // Missing "data" key on 200: must throw.
    @Test(expected = Exception.class)
    public void missingDataKeyThrows() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        TelnyxClient.send(KEY, TO, "test");
    }

    // No auto-retry: a single POST must result in exactly one request regardless of outcome.
    @Test public void noAutoRetryOnHttpError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(503));
        TelnyxClient.send(KEY, TO, "test");
        assertEquals("exactly one request must be made", 1, server.getRequestCount());
    }

    // Connection failure (server closes before responding): must throw, not silently succeed.
    @Test(expected = Exception.class)
    public void connectionResetThrows() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
        TelnyxClient.send(KEY, TO, "test");
    }

    // Status "accepted": returned as-is, no error.
    @Test public void statusAccepted() throws Exception {
        server.enqueue(new MockResponse().setBody(
            "{\"data\":{\"id\":\"" + VALID_ID + "\",\"to\":[{\"status\":\"accepted\"}],\"cost\":null}}"));
        TelnyxClient.Result r = TelnyxClient.status(KEY, VALID_ID);
        assertTrue(r.error.isEmpty());
        assertEquals("accepted", r.status);
        assertEquals(VALID_ID, r.id);
        assertEquals("غير متاحة بعد", r.cost);
    }

    // Status "sent" is distinct from "accepted" and from "delivered".
    @Test public void statusSentDistinctFromAcceptedAndDelivered() throws Exception {
        server.enqueue(new MockResponse().setBody(
            "{\"data\":{\"id\":\"" + VALID_ID + "\",\"to\":[{\"status\":\"sent\"}],\"cost\":null}}"));
        TelnyxClient.Result r = TelnyxClient.status(KEY, VALID_ID);
        assertEquals("sent", r.status);
        assertNotEquals("accepted", r.status);
        assertNotEquals("delivered", r.status);
    }

    // Status "delivered" includes cost when server returns it.
    @Test public void statusDeliveredIncludesCost() throws Exception {
        server.enqueue(new MockResponse().setBody(
            "{\"data\":{\"id\":\"" + VALID_ID + "\",\"to\":[{\"status\":\"delivered\"}]," +
            "\"cost\":{\"amount\":\"0.0075\",\"currency\":\"USD\"}}}"));
        TelnyxClient.Result r = TelnyxClient.status(KEY, VALID_ID);
        assertEquals("delivered", r.status);
        assertTrue("cost must include amount", r.cost.contains("0.0075"));
        assertTrue("cost must include currency", r.cost.contains("USD"));
    }

    // GET for status must not send a body.
    @Test public void statusUsesGetNotPost() throws Exception {
        server.enqueue(new MockResponse().setBody(
            "{\"data\":{\"id\":\"" + VALID_ID + "\",\"to\":[{\"status\":\"sent\"}],\"cost\":null}}"));
        TelnyxClient.status(KEY, VALID_ID);
        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(req);
        assertEquals("GET", req.getMethod());
        assertEquals(0, req.getBodySize());
    }

    // POST for send must use POST, not GET.
    @Test public void sendUsesPost() throws Exception {
        server.enqueue(new MockResponse().setBody(SEND_OK));
        TelnyxClient.send(KEY, TO, "hello");
        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(req);
        assertEquals("POST", req.getMethod());
    }
}
