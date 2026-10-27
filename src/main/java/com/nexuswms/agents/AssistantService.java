package com.nexuswms.agents;

import com.nexuswms.ai.*;
import com.nexuswms.forecasting.ForecastService;
import com.nexuswms.service.*;
import com.nexuswms.voice.*;
import org.slf4j.*;
import org.springframework.ai.chat.messages.*;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;

/**
 * Conversational analytics.
 * <ol>
 *   <li>Recognised commands ("show low stock", "create order for 5 of SKU-1023") are executed deterministically - fast, free, auditable.</li>
 *   <li>Everything else goes to the LLM (Anthropic or OpenAI via Spring AI) with READ-ONLY warehouse tools and a short conversation memory.</li>
 *   <li>If the AI is off or fails (bad key, rate limit, timeout, outage) the user gets a clear note and a rule-based answer from the same data.</li>
 * </ol>
 */
@Service
public class AssistantService {
    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);
    static final int MAX_HISTORY_MESSAGES = 12;

    private final LlmClient llm; private final CommandParser parser; private final CommandExecutor executor;
    private final AnalyticsService analytics; private final WarehouseTools tools;
    private final Deque<Message> history = new ArrayDeque<>();

    public AssistantService(LlmClient llm, CommandParser parser, CommandExecutor executor, AnalyticsService analytics, WarehouseTools tools) {
        this.llm = llm; this.parser = parser; this.executor = executor; this.analytics = analytics; this.tools = tools;
    }

    public boolean aiActive() { return llm.available(); }
    public String modeLabel() { return llm.status(); }
    public synchronized void clearHistory() { history.clear(); }

    /** Streams chunks to onChunk (worker thread) and returns the full text. Never throws for AI problems. */
    public String ask(String question, Long actor, boolean canMutate, Consumer<String> onChunk) {
        String q = question == null ? "" : question.trim();
        if (q.isEmpty()) { String m = "Ask me something about stock, orders, forecasts or approvals."; onChunk.accept(m); return m; }
        if (q.length() > 2000) q = q.substring(0, 2000);

        ParsedCommand cmd = parser.parse(q);
        if (cmd.type() != ParsedCommand.Type.UNKNOWN) { String r = executor.execute(cmd, actor, canMutate); onChunk.accept(r); return r; }

        if (llm.available()) {
            try {
                String answer = llm.chat(systemPrompt(canMutate), snapshot(), q, tools, onChunk);
                if (answer == null || answer.isBlank()) throw new LlmException(LlmException.Kind.BAD_RESPONSE, "Empty answer");
                remember(q, answer);
                return answer;
            } catch (LlmException e) {
                log.warn("Assistant AI call failed ({}): {}", e.kind(), e.getMessage());
                String note = "⚠ " + e.userMessage() + " Answering from local data instead.\n\n";
                onChunk.accept(note);
                String local = ruleBased(q, actor, canMutate);
                onChunk.accept(local);
                return note + local;
            }
        }
        String local = ruleBased(q, actor, canMutate);
        onChunk.accept(local);
        return local;
    }

    private String systemPrompt(boolean canMutate) {
        return "You are the NexusWMS warehouse assistant. Today is " + LocalDate.now() + ". "
                + "Always use the provided read-only tools to get real numbers; never guess stock levels, prices or order data. "
                + "You cannot change anything: you may only PROPOSE actions, and a human approves them under Approvals. "
                + "Never claim an order was created or a stock level changed. The current user " + (canMutate ? "can" : "cannot") + " modify data. "
                + "Be concise; use short lists when helpful.";
    }

    private synchronized List<Message> snapshot() { return new ArrayList<>(history); }

    private synchronized void remember(String q, String a) {
        history.addLast(new UserMessage(q)); history.addLast(new AssistantMessage(a));
        while (history.size() > MAX_HISTORY_MESSAGES) history.removeFirst();
    }

    String ruleBased(String q, Long actor, boolean canMutate) {
        String t = q.toLowerCase();
        if (t.contains("trend")) {
            var s = analytics.valueSeries(12);
            double a = s.get(0).value(), b = s.get(s.size() - 1).value();
            return String.format("Inventory value over 12 weeks: %.0f → %.0f (%+.1f%%).", a, b, a == 0 ? 0 : (b - a) / a * 100);
        }
        if (t.contains("forecast") || t.contains("reorder") || t.contains("replenish")) return executor.execute(new ParsedCommand(ParsedCommand.Type.FORECAST, Map.of()), actor, canMutate);
        if (t.contains("low") || t.contains("stock")) return executor.execute(new ParsedCommand(ParsedCommand.Type.LOW_STOCK, Map.of()), actor, canMutate);
        if (t.contains("value")) return executor.execute(new ParsedCommand(ParsedCommand.Type.INVENTORY_VALUE, Map.of()), actor, canMutate);
        if (t.contains("order") || t.contains("approval") || t.contains("kpi") || t.contains("summary")) {
            var k = analytics.kpis();
            return "SKUs: " + k.skus() + ", low-stock: " + k.lowStock() + ", open POs: " + k.openPos() + ", pending approvals: " + k.pendingApprovals() + ".";
        }
        return CommandExecutor.HELP + " You can also ask about \"trends\" or \"summary\".";
    }
}
