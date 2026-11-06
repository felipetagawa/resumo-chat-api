package com.soften.support.gemini_resumo.docs;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Real SDK against a local server, so the request count shows the SDK's retries. */
class ClaudeServiceTest {

    private static final List<ClaudeService.Turn> TURNS = List.of(new ClaudeService.Turn(true, "oi"));
    private static final String RATE_LIMITED =
            "{\"type\":\"error\",\"error\":{\"type\":\"rate_limit_error\",\"message\":\"slow down\"}}";

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private volatile int status = 429;
    private volatile String body = RATE_LIMITED;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("retry-after-ms", "10");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() { server.stop(0); }

    private void reply(String text, String stopReason) {
        status = 200;
        body = """
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-haiku-5-5",
                 "content":[{"type":"text","text":"%s"}],"stop_reason":"%s","stop_sequence":null,
                 "usage":{"input_tokens":10,"output_tokens":5}}""".formatted(text, stopReason);
    }

    private ClaudeService service() {
        return new ClaudeService(AnthropicOkHttpClient.builder().apiKey("test-key")
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).maxRetries(1).build(), "claude-haiku-5-5");
    }

    private Optional<String> once() { return service().generateOnce("sys", TURNS, 64, Duration.ofSeconds(3), "test"); }

    @Test
    void generateOnceSendsOneRequestSoTheCallerFallsBackWithoutWaitingOutRetryAfter() {
        assertTrue(once().isEmpty());
        assertEquals(1, requests.get());
    }

    @Test
    void generateKeepsItsOneRetryForDocsChatAndSmartReply() {
        assertTrue(service().generate("sys", TURNS, 64, Duration.ofSeconds(3), "test").isEmpty());
        assertEquals(2, requests.get());
    }

    @Test
    void generateOnceWithoutAKeyReturnsEmptyWithoutCallingAnything() {
        assertTrue(new ClaudeService("", "claude-haiku-5-5").generateOnce("sys", TURNS, 64, Duration.ofSeconds(3), "test").isEmpty());
        assertEquals(0, requests.get());
    }

    @Test
    void generateOnceReturnsACompleteReply() {
        reply("NF-e rejeição 539", "end_turn");
        assertEquals(Optional.of("NF-e rejeição 539"), once());
    }

    @Test
    void generateOnceRejectsAReplyCutAtMaxTokensSoGeminiWritesItInstead() {
        reply("- Verifique a chave de acesso na SEFAZ e confi", "max_tokens");
        assertTrue(once().isEmpty());
        assertEquals(1, requests.get());
    }

    @Test
    void generateStillReturnsAReplyCutAtMaxTokensAsOnMain() {
        reply("Para emitir a NF-e, abra", "max_tokens");
        assertEquals(Optional.of("Para emitir a NF-e, abra"),
                service().generate("sys", TURNS, 64, Duration.ofSeconds(3), "test"));
    }
}
