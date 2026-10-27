package com.nexuswms.ai;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SpringAiLlmClient implements LlmClient {

    private ChatClient chatClient; 

    @Value("${nexus.ai.enabled:false}")
    private boolean enabled;

    @Value("${nexus.ai.api-key:}")
    private String apiKey;

    @Value("${nexus.ai.provider:anthropic}")
    private String provider;

    @Value("${nexus.ai.model:claude-3-sonnet}")
    private String model;

    @Value("${nexus.ai.timeout:30}")
    private int timeout;

    @Value("${nexus.ai.max-retries:3}")
    private int maxRetries;

    // 1. Default constructor for Spring Boot
    public SpringAiLlmClient() {
    }

    // 2. THIS IS FOR THE TEST FILE (Fixes the red line in LlmSupportTest)
    public SpringAiLlmClient(boolean enabled, String apiKey, String provider, String model, int timeout, int maxRetries) {
        this.enabled = enabled;
        this.apiKey = apiKey;
        this.provider = provider;
        this.model = model;
        this.timeout = timeout;
        this.maxRetries = maxRetries;
    }

    @Override
    public boolean available() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String status() {
        if (!enabled) return "DISABLED";
        if (apiKey == null || apiKey.isBlank()) return "NO_API_KEY";
        return "READY";
    }

    // 3. THIS IS FOR THE TEST FILE (Fixes the other red line)
    public static String defaultModel(String model) {
        return model;
    }

    @Override
    public String complete(String system, String user) throws LlmException {
        if (!available() || chatClient == null) {
            throw new LlmException(LlmException.Kind.NOT_CONFIGURED, "AI is disabled or missing API key.");
        }
        try {
            return chatClient.prompt()
                    .system(system)
                    .user(user)
                    .call()
                    .content();
        } catch (Exception e) {
            throw classify(e);
        }
    }

    @Override
    public String chat(String system, List<Message> history, String user, Object tools, Consumer<String> onChunk) throws LlmException {
        if (!available() || chatClient == null) {
            throw new LlmException(LlmException.Kind.NOT_CONFIGURED, "AI is disabled or missing API key.");
        }
        try {
            String response = chatClient.prompt()
                    .system(system)
                    .user(user)
                    .call()
                    .content();
            
            if (onChunk != null && response != null) {
                onChunk.accept(response);
            }
            return response;
        } catch (Exception e) {
            throw classify(e);
        }
    }

    public static LlmException classify(Exception e) {
        if (e instanceof SocketTimeoutException) {
            return new LlmException(LlmException.Kind.TIMEOUT, "LLM Timeout", e);
        }
        if (e instanceof UnknownHostException) {
            return new LlmException(LlmException.Kind.UNAVAILABLE, "Network error", e);
        }
        if (e.getMessage() != null && e.getMessage().contains("401")) {
            return new LlmException(LlmException.Kind.AUTH, "Auth failed", e);
        }
        if (e.getMessage() != null && e.getMessage().contains("429")) {
            return new LlmException(LlmException.Kind.RATE_LIMIT, "Rate limited", e);
        }
        return new LlmException(LlmException.Kind.BAD_RESPONSE, e.getMessage(), e);
    }
}