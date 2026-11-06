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
 * Claude (Haiku by default): {@code model} for the documentation chat and Smart Reply, {@code bulkModel}
 * for the problem line, Gerar resumo and Processar dica. Every failure, including a missing
 * ANTHROPIC_API_KEY, returns empty so the caller can fall back to Gemini.
 */
@Service
public class ClaudeService {

    public record Turn(boolean fromUser, String text) {}

    private static final Logger log = LoggerFactory.getLogger(ClaudeService.class);

    private final AnthropicClient client;
    // Same connection pool, no SDK retry: a retry would also wait out Retry-After (up to 60s) first.
    private final AnthropicClient onceClient;
    private final String model;
    // The busiest calls (problem line, summary, tips) stay on their own model, so a bigger model for the
    // docs chat and Smart Reply does not multiply their cost.
    private final String bulkModel;

    @Autowired
    public ClaudeService(@Value("${anthropic.api.key:}") String key,
                         @Value("${anthropic.api.model:claude-haiku-5-5}") String model,
                         @Value("${anthropic.api.bulk-model:claude-haiku-5-5}") String bulkModel) {
        this(key == null || key.isBlank() ? null
                : AnthropicOkHttpClient.builder().apiKey(key.trim()).maxRetries(1).build(), model, bulkModel);
    }

    ClaudeService(AnthropicClient client, String model, String bulkModel) {
        this.client = client;
        this.onceClient = client == null ? null : client.withOptions(options -> options.maxRetries(0));
        this.model = model;
        this.bulkModel = bulkModel;
    }

    public boolean isConfigured() { return client != null; }
    public String model() { return model; }

    /**
     * One attempt within {@code timeout}, for callers whose fallback is Gemini: retrying Claude
     * would only spend the time Gemini needs. A reply cut at {@code maxTokens} is empty too, so
     * Gemini writes it whole, as its own path already rejects MAX_TOKENS. Answered by {@code bulkModel}.
     */
    public Optional<String> generateOnce(String system, List<Turn> turns, long maxTokens, Duration timeout, String feature) {
        return call(onceClient, bulkModel, true, system, turns, maxTokens, timeout, feature);
    }

    /** Up to two attempts (the SDK retries once), each within {@code timeout}. */
    public Optional<String> generate(String system, List<Turn> turns, long maxTokens, Duration timeout, String feature) {
        return call(client, model, false, system, turns, maxTokens, timeout, feature);
    }

    private Optional<String> call(AnthropicClient client, String model, boolean onlyComplete, String system, List<Turn> turns,
                                  long maxTokens, Duration timeout, String feature) {
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
            if (onlyComplete && response.stopReason().map(StopReason.MAX_TOKENS::equals).orElse(false)) return Optional.empty();
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
