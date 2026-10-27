package com.nexuswms.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexuswms.ai.*;
import com.nexuswms.forecasting.*;
import com.nexuswms.service.SettingsService;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The LLM path: valid JSON becomes proposals, bad JSON is repaired once, failures fall back to deterministic rules. */
class ReplenishmentAgentAiTest {
    LlmClient llm = mock(LlmClient.class);
    ReplenishmentAgent agent = new ReplenishmentAgent(llm, new ObjectMapper());
    SettingsService settings = mock(SettingsService.class);
    ReorderRecommendation boxes = new ReorderRecommendation(1L, "SKU-1", "Boxes", 7L, 3, 0, 20.0, 5, 22, new BigDecimal("2.00"));
    ReorderRecommendation tape = new ReorderRecommendation(2L, "SKU-2", "Tape", 7L, 1, 0, 9.0, 2, 10, new BigDecimal("1.50"));

    AgentContext ctx(ReorderRecommendation... r) {
        when(settings.poThreshold()).thenReturn(1000.0);
        return new AgentContext(List.of(r), List.of(), List.of(), mock(ForecastService.class), settings, LocalDate.now());
    }
    @BeforeEach void setUp() { when(llm.available()).thenReturn(true); when(llm.status()).thenReturn("AI mode: test"); }

    @Test void validJsonBecomesProposalsWithServerSideCost() throws Exception {
        when(llm.complete(anyString(), anyString())).thenReturn("```json\n{\"proposals\":[{\"sku\":\"sku-1\",\"quantity\":30,\"rationale\":\"Demand is rising; supplier is slow.\"}],\"skipped\":[{\"sku\":\"SKU-2\",\"reason\":\"enough\"}]}\n```");
        List<ProposedAction> out = agent.propose(ctx(boxes, tape));
        assertEquals(1, out.size());
        ProposedAction p = out.get(0);
        assertEquals(AgentAction.CREATE_PO, p.action()); assertEquals(1L, p.productId()); assertEquals(7L, p.supplierId()); assertEquals(30, p.quantity());
        assertEquals(new BigDecimal("60.00"), p.estimatedCost());           // recomputed locally, never trusted from the model
        assertTrue(p.rationale().startsWith("AI: Demand is rising")); assertFalse(agent.degraded()); assertTrue(agent.diagnostics().startsWith("AI"));
    }
    @Test void unknownSkusAndInvalidQuantitiesAreDroppedAndOversizedOnesClamped() throws Exception {
        when(llm.complete(anyString(), anyString())).thenReturn("{\"proposals\":[{\"sku\":\"HACK-1\",\"quantity\":5,\"rationale\":\"x\"},"
                + "{\"sku\":\"SKU-1\",\"quantity\":-4,\"rationale\":\"neg\"},{\"sku\":\"SKU-2\",\"quantity\":\"500\",\"rationale\":\"too many\"},"
                + "{\"sku\":\"SKU-1\",\"quantity\":\"abc\",\"rationale\":\"nan\"}]}");
        List<ProposedAction> out = agent.propose(ctx(boxes, tape));
        assertEquals(1, out.size()); assertEquals(2L, out.get(0).productId());
        assertEquals(30, out.get(0).quantity());                            // 3 x recommended 10
        assertTrue(out.get(0).rationale().contains("capped at 30"));
    }
    @Test void duplicateSkuAndBlankRationaleHandled() throws Exception {
        when(llm.complete(anyString(), anyString())).thenReturn("{\"proposals\":[{\"sku\":\"SKU-1\",\"quantity\":5},{\"sku\":\"SKU-1\",\"quantity\":9,\"rationale\":\"dup\"}]}");
        List<ProposedAction> out = agent.propose(ctx(boxes));
        assertEquals(1, out.size()); assertEquals(5, out.get(0).quantity()); assertTrue(out.get(0).rationale().contains("Baseline reorder"));
    }
    @Test void unparseableJsonIsRepairedOnce() throws Exception {
        when(llm.complete(anyString(), anyString())).thenReturn("Sorry, I cannot do JSON today")
                .thenReturn("{\"proposals\":[{\"sku\":\"SKU-1\",\"quantity\":12,\"rationale\":\"ok\"}]}");
        List<ProposedAction> out = agent.propose(ctx(boxes));
        assertEquals(1, out.size()); assertEquals(12, out.get(0).quantity()); verify(llm, times(2)).complete(anyString(), anyString());
    }
    @Test void persistentlyBadJsonFallsBackToRules() throws Exception {
        when(llm.complete(anyString(), anyString())).thenReturn("nope", "still nope");
        List<ProposedAction> out = agent.propose(ctx(boxes, tape));
        assertEquals(2, out.size()); assertEquals(22, out.get(0).quantity()); assertTrue(out.get(0).rationale().startsWith("[rule-based"));
        assertTrue(agent.degraded()); assertTrue(agent.diagnostics().contains("BAD_RESPONSE"));
    }
    @Test void invalidApiKeyFallsBackAndFlagsDegraded() throws Exception {
        when(llm.complete(anyString(), anyString())).thenThrow(new LlmException(LlmException.Kind.AUTH, "401 invalid x-api-key"));
        List<ProposedAction> out = agent.propose(ctx(boxes));
        assertEquals(1, out.size()); assertEquals(22, out.get(0).quantity());
        assertTrue(agent.degraded()); assertTrue(agent.diagnostics().contains("AUTH")); verify(llm, times(1)).complete(anyString(), anyString());
    }
    @Test void missingProposalsArrayIsRejected() throws Exception {
        when(llm.complete(anyString(), anyString())).thenReturn("{\"result\":[]}");
        List<ProposedAction> out = agent.propose(ctx(boxes));
        assertEquals(1, out.size()); assertTrue(out.get(0).rationale().startsWith("[rule-based"));      // fell back
    }
    @Test void aiDisabledUsesRulesWithoutCallingTheModel() throws Exception {
        when(llm.available()).thenReturn(false); when(llm.status()).thenReturn("AI disabled");
        List<ProposedAction> out = agent.propose(ctx(boxes));
        assertEquals(1, out.size()); verify(llm, never()).complete(anyString(), anyString()); assertFalse(agent.degraded());
    }
    @Test void nothingToReorderMeansNoCall() throws Exception {
        ReorderRecommendation none = new ReorderRecommendation(3L, "SKU-3", "Ok", 7L, 99, 0, 1.0, 1, 0, BigDecimal.ONE);
        assertTrue(agent.propose(ctx(none)).isEmpty()); verify(llm, never()).complete(anyString(), anyString());
    }
}
