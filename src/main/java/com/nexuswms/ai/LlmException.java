package com.nexuswms.ai;

/** Classified LLM failure so callers can decide between "fix your key", "retry later" and "fall back". */
public class LlmException extends Exception {
    public enum Kind { NOT_CONFIGURED, AUTH, RATE_LIMIT, TIMEOUT, UNAVAILABLE, BAD_RESPONSE }
    private final Kind kind;
    public LlmException(Kind kind, String message) { super(message); this.kind = kind; }
    public LlmException(Kind kind, String message, Throwable cause) { super(message, cause); this.kind = kind; }
    public Kind kind() { return kind; }

    /** Message safe to show an end user. */
    public String userMessage() {
        return switch (kind) {
            case NOT_CONFIGURED -> getMessage();
            case AUTH -> "The AI provider rejected the API key. Check NEXUS_AI_API_KEY and NEXUS_AI_PROVIDER, then restart.";
            case RATE_LIMIT -> "The AI provider is rate-limiting requests. Pausing AI calls for a minute.";
            case TIMEOUT -> "The AI provider did not answer in time.";
            case UNAVAILABLE -> "The AI provider is unreachable right now.";
            case BAD_RESPONSE -> "The AI returned an answer that could not be understood.";
        };
    }
}
