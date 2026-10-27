package com.nexuswms.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Tolerant extraction of a JSON object from an LLM reply (strips ```json fences and surrounding prose). */
public final class LlmJson {
    private LlmJson() {}

    public static String extractObject(String raw) throws LlmException {
        if (raw == null || raw.isBlank()) throw new LlmException(LlmException.Kind.BAD_RESPONSE, "Empty AI response");
        String s = raw.trim();
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            s = nl > 0 ? s.substring(nl + 1) : s.substring(3);
            int end = s.lastIndexOf("```");
            if (end >= 0) s = s.substring(0, end);
        }
        int a = s.indexOf('{'), b = s.lastIndexOf('}');
        if (a < 0 || b <= a) throw new LlmException(LlmException.Kind.BAD_RESPONSE, "No JSON object in AI response");
        return s.substring(a, b + 1);
    }

    public static JsonNode parseObject(ObjectMapper mapper, String raw) throws LlmException {
        try {
            JsonNode n = mapper.readTree(extractObject(raw));
            if (n == null || !n.isObject()) throw new LlmException(LlmException.Kind.BAD_RESPONSE, "AI response is not a JSON object");
            return n;
        } catch (JsonProcessingException e) {
            throw new LlmException(LlmException.Kind.BAD_RESPONSE, "Invalid JSON from AI: " + e.getOriginalMessage(), e);
        }
    }
}
