package com.soften.support.gemini_resumo.docs;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;
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
    private volatile String lastRequest;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            lastRequest = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
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

    private ClaudeService service() { return service("claude-haiku-5-5", "claude-haiku-5-5"); }

    private ClaudeService service(String model, String bulkModel) {
        return new ClaudeService(AnthropicOkHttpClient.builder().apiKey("test-key")
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).maxRetries(1).build(), model, bulkModel);
    }

    /** Runs {@code call} with an appender on ClaudeService's logger; returns the formatted log lines. */
    private static List<String> logsOf(Runnable call) {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ClaudeService.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start(); logger.addAppender(appender);
        try { call.run(); }
        finally { logger.detachAppender(appender); appender.stop(); }
        return appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void summaryTipsAndTheProblemLineUseTheBulkModelSoTheMainModelCanGrowWithoutThem() {
        reply("ok", "end_turn");
        ClaudeService claude = service("claude-sonnet-5-5", "claude-haiku-5-5");

        List<String> once = logsOf(() -> claude.generateOnce("sys", TURNS, 64, Duration.ofSeconds(3), "summary"));
        assertEquals("claude-haiku-5-5", new JSONObject(lastRequest).getString("model"));
        assertTrue(once.stream().anyMatch(l -> l.startsWith("claude_usage feature=summary model=claude-haiku-5-5 ")), once.toString());

        List<String> chat = logsOf(() -> claude.generate("sys", TURNS, 64, Duration.ofSeconds(3), "smart_reply"));
        assertEquals("claude-sonnet-5-5", new JSONObject(lastRequest).getString("model"));
        assertTrue(chat.stream().anyMatch(l -> l.startsWith("claude_usage feature=smart_reply model=claude-sonnet-5-5 ")), chat.toString());
        assertEquals("claude-sonnet-5-5", claude.model(), "docs chat reports the model that answered it");
    }

    @Test
    void theProblemLineTurnsThinkingOffWhileSummaryAndTipsKeepTheModelDefault() {
        reply("NF-e rejeição 539", "end_turn");
        ClaudeService claude = service("claude-sonnet-5-5", "claude-haiku-5-5");

        assertEquals(Optional.of("NF-e rejeição 539"),
                claude.generateOnceWithoutThinking("sys", TURNS, 128, Duration.ofSeconds(3), "problem_line"));
        JSONObject label = new JSONObject(lastRequest);
        assertEquals("disabled", label.getJSONObject("thinking").getString("type"));
        // Haiku 5.5 accepts thinking off only at effort high or below.
        assertEquals("low", label.getJSONObject("output_config").getString("effort"));
        assertEquals("claude-haiku-5-5", label.getString("model"));
        assertEquals(1, requests.get(), "one attempt, like generateOnce");

        claude.generateOnce("sys", TURNS, 64, Duration.ofSeconds(3), "summary");
        assertFalse(new JSONObject(lastRequest).has("thinking"));
        claude.generate("sys", TURNS, 64, Duration.ofSeconds(3), "smart_reply");
        assertFalse(new JSONObject(lastRequest).has("thinking"));
    }

    @Test
    void theProblemLineStillRejectsAReplyCutAtMaxTokens() {
        reply("NF-e rejeição", "max_tokens");
        assertTrue(service().generateOnceWithoutThinking("sys", TURNS, 128, Duration.ofSeconds(3), "problem_line").isEmpty());
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
        assertTrue(new ClaudeService("", "claude-haiku-5-5", "claude-haiku-5-5").generateOnce("sys", TURNS, 64, Duration.ofSeconds(3), "test").isEmpty());
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
