package com.nexuswms.service;

import com.nexuswms.model.*;
import com.nexuswms.repository.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SupplierServiceTest {
    @Test void scoreFormula() {
        assertEquals(new BigDecimal("0.80"), SupplierService.score(0, 0));
        assertEquals(new BigDecimal("0.93"), SupplierService.score(10, 10));
        assertEquals(new BigDecimal("0.40"), SupplierService.score(0, 5));
    }
    @Test void recomputeCountsOnTimeReceipts() {
        SupplierRepository sr = mock(SupplierRepository.class); PurchaseOrderRepository or = mock(PurchaseOrderRepository.class);
        SupplierService svc = new SupplierService(sr, or, mock(AuditService.class));
        Supplier s = new Supplier(); s.id = 1L;
        when(sr.findById(1L)).thenReturn(Optional.of(s));
        PurchaseOrder onTime = received(LocalDate.now(), LocalDate.now()), late = received(LocalDate.now().minusDays(3), LocalDate.now()), open = new PurchaseOrder();
        open.status = OrderStatus.CONFIRMED;
        when(or.findBySupplierIdOrderByCreatedAtDescIdDesc(1L)).thenReturn(List.of(onTime, late, open));
        assertEquals(new BigDecimal("0.71"), svc.recomputeReliability(1L)); // (1+4)/(2+5)
    }
    private PurchaseOrder received(LocalDate expected, LocalDate receivedOn) {
        PurchaseOrder o = new PurchaseOrder(); o.status = OrderStatus.RECEIVED; o.expectedDate = expected; o.updatedAt = receivedOn.atTime(12, 0); return o;
    }
    @Test void duplicateNameRejected() {
        SupplierRepository sr = mock(SupplierRepository.class);
        SupplierService svc = new SupplierService(sr, mock(PurchaseOrderRepository.class), mock(AuditService.class));
        Supplier other = new Supplier(); other.id = 2L;
        when(sr.findByNameIgnoreCase("Acme")).thenReturn(Optional.of(other));
        SupplierForm f = new SupplierForm(); f.name = "Acme";
        assertThrows(BusinessException.class, () -> svc.save(null, f, 1L));
    }

    @Test void supplierWithOpenOrdersCannotBeDeleted() {
        SupplierRepository sr = mock(SupplierRepository.class); PurchaseOrderRepository or = mock(PurchaseOrderRepository.class);
        SupplierService svc = new SupplierService(sr, or, mock(AuditService.class));
        Supplier s = new Supplier(); s.id = 1L; s.name = "Acme"; when(sr.findById(1L)).thenReturn(Optional.of(s));
        when(or.existsBySupplierIdAndStatusIn(eq(1L), any())).thenReturn(true);
        BusinessException e = assertThrows(BusinessException.class, () -> svc.delete(1L, 1L));
        assertTrue(e.getMessage().contains("open purchase orders"));
        verify(sr, never()).delete(any()); assertTrue(s.active);
    }
    @Test void supplierWithOnlyHistoryIsSoftDeleted() {
        SupplierRepository sr = mock(SupplierRepository.class); PurchaseOrderRepository or = mock(PurchaseOrderRepository.class);
        SupplierService svc = new SupplierService(sr, or, mock(AuditService.class));
        Supplier s = new Supplier(); s.id = 1L; s.name = "Acme"; when(sr.findById(1L)).thenReturn(Optional.of(s));
        when(or.existsBySupplierId(1L)).thenReturn(true);
        assertTrue(svc.delete(1L, 1L)); assertFalse(s.active); verify(sr, never()).delete(any());
    }
    @Test void invalidEmailRejected() {
        SupplierService svc = new SupplierService(mock(SupplierRepository.class), mock(PurchaseOrderRepository.class), mock(AuditService.class));
        SupplierForm f = new SupplierForm(); f.name = "X"; f.contactEmail = "not-an-email";
        assertThrows(BusinessException.class, () -> svc.save(null, f, 1L));
    }
}
