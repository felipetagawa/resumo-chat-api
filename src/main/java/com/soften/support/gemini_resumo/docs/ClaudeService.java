package com.soften.support.gemini_resumo.docs;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.RequestOptions;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.TextBlockParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Claude (Haiku by default) for the documentation chat and Smart Reply. Every failure,
 * including a missing ANTHROPIC_API_KEY, returns empty so the caller can fall back to Gemini.
 */
@Service
public class ClaudeService {

    public record Turn(boolean fromUser, String text) {}

    private static final Logger log = LoggerFactory.getLogger(ClaudeService.class);

    private final AnthropicClient client;
    private final String model;

    @Autowired
    public ClaudeService(@Value("${anthropic.api.key:}") String key,
                         @Value("${anthropic.api.model:claude-haiku-5-5}") String model) {
        this.model = model;
        this.client = key == null || key.isBlank() ? null
                : AnthropicOkHttpClient.builder().apiKey(key.trim()).maxRetries(1).build();
    }

    ClaudeService(AnthropicClient client, String model) {
        this.client = client;
        this.model = model;
    }

    public boolean isConfigured() { return client != null; }
    public String model() { return model; }

    public Optional<String> generate(String system, List<Turn> turns, long maxTokens, Duration timeout, String feature) {
        if (client == null || turns.isEmpty()) return Optional.empty();
        long started = System.nanoTime();
        try {
            MessageCreateParams.Builder params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(maxTokens)
                    // The system prompt is identical across requests: cache it.
                    .systemOfTextBlockParams(List.of(TextBlockParam.builder().text(system)
                            .cacheControl(CacheControlEphemeral.builder().build()).build()))
                    // Short answers to support questions: low effort keeps latency down.
                    .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build());
            for (Turn turn : turns) {
                if (turn.fromUser()) params.addUserMessage(turn.text());
                else params.addAssistantMessage(turn.text());
            }
            Message response = client.messages().create(params.build(),
                    RequestOptions.builder().timeout(timeout).build());
            long ms = Duration.ofNanos(System.nanoTime() - started).toMillis();
            String stop = response.stopReason().map(StopReason::toString).orElse("none");
            log.info("claude_usage feature={} model={} stop={} inputTokens={} outputTokens={} cacheRead={} durationMs={}",
                    feature, model, stop, response.usage().inputTokens(), response.usage().outputTokens(),
                    response.usage().cacheReadInputTokens().orElse(0L), ms);
            if (response.stopReason().map(StopReason.REFUSAL::equals).orElse(false)) return Optional.empty();
            String text = response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(TextBlock::text)
                    .collect(Collectors.joining()).trim();
            return text.isEmpty() ? Optional.empty() : Optional.of(text);
        } catch (Exception e) {
            // Never log request content; the class name is enough to tell timeout from auth or 5xx.
            log.warn("Claude call failed. feature={} model={} error={} durationMs={}", feature, model,
                    e.getClass().getSimpleName(), Duration.ofNanos(System.nanoTime() - started).toMillis());
            return Optional.empty();
        }
    }
}
