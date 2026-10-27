package com.nexuswms.agents;

import com.nexuswms.forecasting.ForecastService;
import com.nexuswms.model.*;
import com.nexuswms.repository.*;
import com.nexuswms.service.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentOrchestratorTest {
    ForecastService forecasts = mock(ForecastService.class);
    InventoryRepository inventory = mock(InventoryRepository.class);
    PurchaseOrderRepository orders = mock(PurchaseOrderRepository.class);
    ApprovalRequestRepository approvals = mock(ApprovalRequestRepository.class);
    SettingsService settings = mock(SettingsService.class);
    AuditService audit = mock(AuditService.class);
    NotificationService notes = mock(NotificationService.class);
    Agent a1 = mock(Agent.class), a2 = mock(Agent.class);

    ProposedAction po(String agent, long product) {
        return new ProposedAction(agent, AgentAction.CREATE_PO, product, 1L, null, 5, BigDecimal.TEN, null, "why");
    }
    AgentOrchestrator orch() { return new AgentOrchestrator(List.of(a1, a2), forecasts, inventory, orders, approvals, settings, audit, notes, mock(org.springframework.transaction.PlatformTransactionManager.class)); }

    @BeforeEach void setUp() {
        when(forecasts.recommendations()).thenReturn(List.of()); when(inventory.findAllActive()).thenReturn(List.of());
        when(orders.findByStatusIn(any())).thenReturn(List.of());
        when(a1.name()).thenReturn("A1"); when(a1.settingsKey()).thenReturn("a1"); when(a2.name()).thenReturn("A2"); when(a2.settingsKey()).thenReturn("a2");
        when(settings.agentEnabled(anyString())).thenReturn(true);
    }

    @Test void deduplicatesAcrossAgentsAndPersistsPending() {
        when(a1.propose(any())).thenReturn(List.of(po("A1", 1), po("A1", 2)));
        when(a2.propose(any())).thenReturn(List.of(po("A2", 1)));
        assertEquals(2, orch().runAll());
        verify(approvals, times(2)).save(any(ApprovalRequest.class));
        verify(audit).log(eq("AGENT"), any(), eq("RUN"), any(), contains("created=2"));
    }
    @Test void skipsDisabledAgents() {
        when(settings.agentEnabled("a1")).thenReturn(false);
        when(a2.propose(any())).thenReturn(List.of());
        orch().runAll();
        verify(a1, never()).propose(any()); verify(a2).propose(any());
    }
    @Test void skipsProposalsAlreadyPending() {
        when(a1.propose(any())).thenReturn(List.of(po("A1", 1))); when(a2.propose(any())).thenReturn(List.of());
        when(approvals.existsByDedupKeyAndStatus("PO:1", ApprovalStatus.PENDING)).thenReturn(true);
        assertEquals(0, orch().runAll());
        verify(approvals, never()).save(any());
    }
    @Test void aFailingAgentDoesNotStopOthers() {
        when(a1.propose(any())).thenThrow(new IllegalStateException("boom")); when(a2.propose(any())).thenReturn(List.of(po("A2", 3)));
        assertEquals(1, orch().runAll());
    }
    @Test void statusChangeDedupKeyIncludesOrderAndTarget() {
        ProposedAction p = new ProposedAction("O", AgentAction.ORDER_STATUS_CHANGE, null, 1L, 9L, 0, BigDecimal.ZERO, "SUBMITTED", "r");
        assertEquals("ORD:9:SUBMITTED", p.dedupKey());
    }

    @Test void overlappingRunsAreRejected() throws Exception {
        java.util.concurrent.CountDownLatch inside = new java.util.concurrent.CountDownLatch(1), release = new java.util.concurrent.CountDownLatch(1);
        when(a1.propose(any())).thenAnswer(i -> { inside.countDown(); release.await(5, java.util.concurrent.TimeUnit.SECONDS); return List.of(); });
        when(a2.propose(any())).thenReturn(List.of());
        AgentOrchestrator o = orch();
        Thread t = new Thread(o::runAll); t.start();
        assertTrue(inside.await(5, java.util.concurrent.TimeUnit.SECONDS));
        assertThrows(BusinessException.class, o::runAll);
        release.countDown(); t.join(5000);
    }
    @Test void degradedAgentRaisesWarning() {
        when(a1.propose(any())).thenReturn(List.of()); when(a1.degraded()).thenReturn(true); when(a2.propose(any())).thenReturn(List.of());
        orch().runAll();
        verify(notes).publish(eq("WARN"), contains("AI was unavailable"));
    }
}
