package com.nettrace;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests against the real /api/run HTTP handler (not a
 * reimplementation of its logic) -- these exist specifically to cover the
 * parts NetTraceEngineTest explicitly calls out as untested: query
 * validation, error responses, and the hand-built JSON's structural
 * correctness under adversarial input.
 *
 * Spins up NetTraceServer.ApiRunHandler on an ephemeral local port for the
 * whole test class and issues real HTTP requests against it with
 * java.net.http.HttpClient.
 */
@DisplayName("/api/run HTTP handler tests")
class NetTraceServerApiTest {

    private static HttpServer server;
    private static String baseUrl;
    private static final HttpClient client = HttpClient.newHttpClient();

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/run", new NetTraceServer.ApiRunHandler());
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) server.stop(0);
    }

    private HttpResponse<String> get(String queryString) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/run" + (queryString.isEmpty() ? "" : "?" + queryString)))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Structural JSON validity check that mirrors how this codebase builds
     * JSON by hand: walks the string tracking whether we're inside a
     * string literal (toggling on an UNESCAPED quote) and, only outside of
     * string literals, tracks brace/bracket balance. This is exactly the
     * invariant a missing jsonEscape() call breaks -- an unescaped quote
     * inside a value prematurely closes the string and desyncs every
     * brace/bracket count that follows, which is what this test would
     * catch even without a full JSON parser on the classpath.
     */
    private void assertWellFormedJson(String json) {
        boolean inString = false;
        boolean escaped = false;
        int braceDepth = 0;
        int bracketDepth = 0;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> inString = true;
                case '{' -> braceDepth++;
                case '}' -> braceDepth--;
                case '[' -> bracketDepth++;
                case ']' -> bracketDepth--;
                default -> { /* ignore */ }
            }
            assertTrue(braceDepth >= 0, "Unbalanced '}' encountered -- malformed JSON: " + json);
            assertTrue(bracketDepth >= 0, "Unbalanced ']' encountered -- malformed JSON: " + json);
        }

        assertFalse(inString, "JSON ended mid-string-literal -- an unescaped quote broke the structure: " + json);
        assertEquals(0, braceDepth, "Unbalanced braces -- malformed JSON: " + json);
        assertEquals(0, bracketDepth, "Unbalanced brackets -- malformed JSON: " + json);
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("Default request returns 200 with well-formed JSON containing the expected top-level fields")
        void defaultRequestSucceeds() throws Exception {
            HttpResponse<String> res = get("");

            assertEquals(200, res.statusCode());
            assertEquals("application/json", res.headers().firstValue("Content-Type").orElse(""));
            assertWellFormedJson(res.body());
            for (String field : new String[]{
                    "tax_ns", "tax_percent", "model_a_ratio", "detected_threats",
                    "throughput_pps", "packet_loss_pct", "hops", "dynamic_route",
                    "bypassed_firewall", "enforced_firewall", "packet_stream"
            }) {
                assertTrue(res.body().contains("\"" + field + "\""), "Response should contain field: " + field);
            }
        }

        @Test
        @DisplayName("enforceFirewall defaults to true when the param is omitted")
        void enforceFirewallDefaultsTrue() throws Exception {
            HttpResponse<String> res = get("");
            assertEquals(200, res.statusCode());
            assertTrue(res.body().contains("\"enforced_firewall\": true"));
            assertTrue(res.body().contains("\"bypassed_firewall\": false"),
                    "Enforced mode must never report a bypassed route");
        }

        @Test
        @DisplayName("enforceFirewall=false is honored and reflected in the response")
        void enforceFirewallFalseIsHonored() throws Exception {
            HttpResponse<String> res = get("enforceFirewall=false");
            assertEquals(200, res.statusCode());
            assertTrue(res.body().contains("\"enforced_firewall\": false"));
        }

        @Test
        @DisplayName("OPTIONS preflight returns 204 with no body")
        void optionsReturnsNoContent() throws Exception {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/run"))
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> res = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(204, res.statusCode());
        }
    }

    @Nested
    @DisplayName("Input validation")
    class InputValidation {

        @Test
        @DisplayName("Non-numeric batchSize returns 400 with an error body instead of crashing the connection")
        void nonNumericBatchSizeReturns400() throws Exception {
            HttpResponse<String> res = get("batchSize=not-a-number");

            assertEquals(400, res.statusCode());
            assertWellFormedJson(res.body());
            assertTrue(res.body().contains("\"error\""));
        }

        @Test
        @DisplayName("batchSize of 0 is rejected as out of range")
        void zeroBatchSizeReturns400() throws Exception {
            HttpResponse<String> res = get("batchSize=0");
            assertEquals(400, res.statusCode());
        }

        @Test
        @DisplayName("Negative batchSize is rejected as out of range")
        void negativeBatchSizeReturns400() throws Exception {
            HttpResponse<String> res = get("batchSize=-5");
            assertEquals(400, res.statusCode());
        }

        @Test
        @DisplayName("Excessively large batchSize is rejected as out of range")
        void hugeBatchSizeReturns400() throws Exception {
            HttpResponse<String> res = get("batchSize=999999999");
            assertEquals(400, res.statusCode());
        }

        @Test
        @DisplayName("A valid batchSize at the boundary (1 and 1000) succeeds")
        void boundaryBatchSizesSucceed() throws Exception {
            assertEquals(200, get("batchSize=1").statusCode());
            assertEquals(200, get("batchSize=1000").statusCode());
        }

        @Test
        @DisplayName("Overlong srcIp/dstIp values are rejected as bad input rather than accepted verbatim")
        void overlongIpReturns400() throws Exception {
            String longValue = "1".repeat(200);
            HttpResponse<String> res = get("srcIp=" + longValue);
            assertEquals(400, res.statusCode());
        }
    }

    @Nested
    @DisplayName("JSON safety under adversarial input")
    class JsonSafety {

        @Test
        @DisplayName("A srcIp containing a double quote does not break the JSON response")
        void quoteInSrcIpDoesNotBreakJson() throws Exception {
            // URL-encoded '"' -- exercises both the escaping fix and the
            // urlDecode() fix in the same request.
            HttpResponse<String> res = get("srcIp=evil%22ip");

            assertEquals(200, res.statusCode());
            assertWellFormedJson(res.body());
            // The escaped quote should appear in the packet_stream entries
            // that echo srcIp back, proving the value was both decoded and
            // safely escaped rather than dropped or left broken.
            assertTrue(res.body().contains("evil\\\"ip"),
                    "srcIp should be present, URL-decoded, and JSON-escaped in the response");
        }

        @Test
        @DisplayName("A dstIp containing a backslash does not break the JSON response")
        void backslashInDstIpDoesNotBreakJson() throws Exception {
            HttpResponse<String> res = get("dstIp=evil%5Cip");

            assertEquals(200, res.statusCode());
            assertWellFormedJson(res.body());
        }

        @Test
        @DisplayName("A srcIp containing a raw newline does not break the JSON response")
        void newlineInSrcIpDoesNotBreakJson() throws Exception {
            HttpResponse<String> res = get("srcIp=evil%0Aip");

            assertEquals(200, res.statusCode());
            assertWellFormedJson(res.body());
        }
    }
}
