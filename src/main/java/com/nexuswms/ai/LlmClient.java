package com.nexuswms.ai;

import org.springframework.ai.chat.messages.Message;
import java.util.List;
import java.util.function.Consumer;

/** Provider-neutral gateway to the LLM (Anthropic or OpenAI). Implementations never throw unchecked exceptions for provider errors. */
public interface LlmClient {
    /** True when AI is enabled, a key is configured, no auth failure is latched and the circuit breaker is closed. */
    boolean available();
    /** Human readable state for the UI ("AI mode: anthropic / model", "disabled", "API key rejected", ...). */
    String status();
    /** Single-shot completion. Blocking, bounded by nexus.ai.timeout-seconds. */
    String complete(String system, String user) throws LlmException;
    /** Conversational call with read-only tools; streams chunks to onChunk and returns the full text. */
    String chat(String system, List<Message> history, String user, Object tools, Consumer<String> onChunk) throws LlmException;
}
