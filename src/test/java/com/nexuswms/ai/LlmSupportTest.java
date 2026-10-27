package com.nexuswms.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static com.nexuswms.ai.LlmException.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

class LlmSupportTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void extractsJsonFromFencesAndProse() throws Exception {
        assertEquals("{\"a\":1}", LlmJson.extractObject("```json\n{\"a\":1}\n```"));
        assertEquals("{\"a\":1}", LlmJson.extractObject("Sure! Here you go: {\"a\":1} Hope that helps."));
        assertEquals(1, LlmJson.parseObject(mapper, "{\"a\":1}").get("a").asInt());
    }
    @Test void badOutputIsClassifiedAsBadResponse() {
        assertEquals(BAD_RESPONSE, assertThrows(LlmException.class, () -> LlmJson.extractObject("no json here")).kind());
        assertEquals(BAD_RESPONSE, assertThrows(LlmException.class, () -> LlmJson.parseObject(mapper, "{\"a\": }")).kind());
        assertEquals(BAD_RESPONSE, assertThrows(LlmException.class, () -> LlmJson.extractObject("  ")).kind());
    }
    @Test void providerErrorsAreClassified() {
        assertEquals(AUTH, SpringAiLlmClient.classify(new RuntimeException("HTTP 401 - invalid x-api-key")).kind());
        assertEquals(AUTH, SpringAiLlmClient.classify(new RuntimeException("wrapper", new RuntimeException("Incorrect API key provided"))).kind());
        assertEquals(RATE_LIMIT, SpringAiLlmClient.classify(new RuntimeException("429 Too Many Requests")).kind());
        assertEquals(TIMEOUT, SpringAiLlmClient.classify(new java.net.SocketTimeoutException("Read timed out")).kind());
        assertEquals(UNAVAILABLE, SpringAiLlmClient.classify(new java.net.UnknownHostException("api.anthropic.com")).kind());
    }
    @Test void disabledOrKeylessClientIsNotAvailableAndFailsFast() {
        SpringAiLlmClient off = new SpringAiLlmClient(false, "anthropic", "key", "", 30, 1000);
        assertFalse(off.available()); assertTrue(off.status().contains("disabled"));
        assertEquals(NOT_CONFIGURED, assertThrows(LlmException.class, () -> off.complete("s", "u")).kind());
        SpringAiLlmClient nokey = new SpringAiLlmClient(true, "openai", " ", "", 30, 1000);
        assertFalse(nokey.available()); assertTrue(nokey.status().contains("API_KEY"));
        SpringAiLlmClient bad = new SpringAiLlmClient(true, "gemini", "k", "", 30, 1000);
        assertFalse(bad.available()); assertTrue(bad.status().contains("Unknown AI provider"));
    }
    @Test void defaultModelsPerProvider() {
        assertEquals("gpt-4o-mini", SpringAiLlmClient.defaultModel("openai"));
        assertTrue(SpringAiLlmClient.defaultModel("anthropic").startsWith("claude"));
    }
}
