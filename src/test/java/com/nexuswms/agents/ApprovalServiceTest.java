package com.nexuswms.agents;

import com.nexuswms.model.*;
import com.nexuswms.repository.*;
import com.nexuswms.service.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ApprovalServiceTest {
    ApprovalRequestRepository approvals = mock(ApprovalRequestRepository.class);
    OrderService orders = mock(OrderService.class);
    UserRepository users = mock(UserRepository.class);
    AuditService audit = mock(AuditService.class);
    ApprovalService svc;
    ApprovalRequest req;

    @BeforeEach void setUp() {
        svc = new ApprovalService(approvals, orders, mock(ProductRepository.class), mock(PurchaseOrderRepository.class), mock(SupplierRepository.class), users, audit, mock(NotificationService.class));
        req = new ApprovalRequest(); req.id = 1L; req.action = AgentAction.CREATE_PO; req.agent = "R"; req.productId = 5L; req.supplierId = 2L;
        req.quantity = 7; req.estimatedCost = BigDecimal.TEN; req.status = ApprovalStatus.PENDING;
        when(approvals.findForUpdate(1L)).thenReturn(Optional.of(req));
        PurchaseOrder po = new PurchaseOrder(); po.id = 44L; when(orders.createDraft(any(), any(), any(), any())).thenReturn(po);
    }
    void as(Role r) { User u = new User(); u.id = 9L; u.role = r; when(users.findById(9L)).thenReturn(Optional.of(u)); }

    @Test void operatorCannotApprove() {
        as(Role.OPERATOR);
        assertThrows(BusinessException.class, () -> svc.approve(1L, 9L, null));
        verify(orders, never()).createDraft(any(), any(), any(), any()); assertEquals(ApprovalStatus.PENDING, req.status);
    }
    @Test void managerApprovalExecutesAndAudits() {
        as(Role.MANAGER);
        svc.approve(1L, 9L, "ok");
        verify(orders).createDraft(eq(2L), argThat(l -> l.size() == 1 && l.get(0).quantity() == 7), isNull(), eq(9L));
        verify(orders).submit(44L, 9L);
        assertEquals(ApprovalStatus.APPROVED, req.status); assertEquals(9L, req.decidedBy);
        verify(audit).log(eq("APPROVAL"), eq(1L), eq("APPROVED"), eq(9L), anyString());
    }
    @Test void rejectDoesNotExecute() {
        as(Role.ADMIN); svc.reject(1L, 9L, "no");
        verify(orders, never()).createDraft(any(), any(), any(), any()); assertEquals(ApprovalStatus.REJECTED, req.status);
        verify(audit).log(eq("APPROVAL"), eq(1L), eq("REJECTED"), eq(9L), contains("no"));
    }
    @Test void cannotDecideTwice() { as(Role.ADMIN); req.status = ApprovalStatus.APPROVED; assertThrows(BusinessException.class, () -> svc.reject(1L, 9L, null)); }
    @Test void requestChangesNeedsNote() { as(Role.ADMIN); assertThrows(BusinessException.class, () -> svc.requestChanges(1L, 9L, " ")); }
}
