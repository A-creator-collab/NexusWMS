package com.nexuswms.agents;

import com.nexuswms.forecasting.*;
import com.nexuswms.forecasting.ForecastService.DemandStats;
import com.nexuswms.model.*;
import com.nexuswms.service.SettingsService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Decision rules of the three agents, with rationale text. */
class AgentsDecisionTest {
    SettingsService settings = mock(SettingsService.class);
    ForecastService forecasts = mock(ForecastService.class);

    AgentContext ctx(List<ReorderRecommendation> recs, List<Inventory> inv, List<PurchaseOrder> orders) {
        when(settings.riskThreshold()).thenReturn(0.30);
        return new AgentContext(recs, inv, orders, forecasts, settings, LocalDate.now());
    }

    @Test void replenishmentProposesOnlyPositiveReorders() {
        var r1 = new ReorderRecommendation(1L, "A", "Box", 2L, 3, 0, 20.0, 5, 22, new BigDecimal("2.00"));
        var r2 = new ReorderRecommendation(2L, "B", "Tape", 2L, 50, 0, 5.0, 5, 0, BigDecimal.ONE);
        List<ProposedAction> out = new ReplenishmentAgent(mock(com.nexuswms.ai.LlmClient.class), new com.fasterxml.jackson.databind.ObjectMapper()).propose(ctx(List.of(r1, r2), List.of(), List.of()));
        assertEquals(1, out.size());
        assertEquals(AgentAction.CREATE_PO, out.get(0).action()); assertEquals(22, out.get(0).quantity());
        assertEquals(new BigDecimal("44.00"), out.get(0).estimatedCost()); assertTrue(out.get(0).rationale().contains("reorder 22"));
    }

    @Test void riskAgentFlagsLikelyStockOuts() {
        Supplier s = new Supplier(); s.id = 3L; s.leadTimeDays = 4;
        Product p = new Product(); p.id = 1L; p.sku = "X"; p.supplier = s; p.unitPrice = BigDecimal.ONE;
        Inventory i = new Inventory(); i.product = p; i.quantityOnHand = 5;
        when(forecasts.stats(p)).thenReturn(new DemandStats(new double[90], 10, 3, 10)); // expects 40 over lead time, has 5
        List<ProposedAction> out = new RiskAgent().propose(ctx(List.of(), List.of(i), List.of()));
        assertEquals(1, out.size()); assertEquals(AgentAction.EXPEDITE_ORDER, out.get(0).action());
        assertTrue(out.get(0).rationale().contains("Stock-out probability")); assertTrue(out.get(0).quantity() >= 35);
    }

    @Test void riskAgentIgnoresWellStockedProducts() {
        Supplier s = new Supplier(); s.id = 3L; s.leadTimeDays = 4;
        Product p = new Product(); p.id = 1L; p.supplier = s; p.unitPrice = BigDecimal.ONE;
        Inventory i = new Inventory(); i.product = p; i.quantityOnHand = 500;
        when(forecasts.stats(p)).thenReturn(new DemandStats(new double[90], 10, 3, 10));
        assertTrue(new RiskAgent().propose(ctx(List.of(), List.of(i), List.of())).isEmpty());
    }

    @Test void orderAgentSubmitsIdleDraftsAndCancelsOverdueFromUnreliableSuppliers() {
        Supplier good = new Supplier(); good.id = 1L; good.name = "G"; good.leadTimeDays = 5;
        Supplier bad = new Supplier(); bad.id = 2L; bad.name = "B"; bad.reliabilityScore = new BigDecimal("0.50");
        PurchaseOrder draft = new PurchaseOrder(); draft.id = 1L; draft.orderNumber = "D"; draft.supplier = good; draft.status = OrderStatus.DRAFT; draft.createdAt = LocalDateTime.now().minusDays(3);
        PurchaseOrder late = new PurchaseOrder(); late.id = 2L; late.orderNumber = "L"; late.supplier = bad; late.status = OrderStatus.CONFIRMED;
        late.createdAt = LocalDateTime.now().minusDays(20); late.expectedDate = LocalDate.now().minusDays(10);
        PurchaseOrder fresh = new PurchaseOrder(); fresh.id = 3L; fresh.orderNumber = "F"; fresh.supplier = good; fresh.status = OrderStatus.DRAFT; fresh.createdAt = LocalDateTime.now();
        List<ProposedAction> out = new OrderAgent().propose(ctx(List.of(), List.of(), List.of(draft, late, fresh)));
        assertEquals(2, out.size());
        assertEquals("SUBMITTED", out.get(0).newStatus()); assertEquals("CANCELLED", out.get(1).newStatus());
    }
}
