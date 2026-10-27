package com.nexuswms.service;

import com.nexuswms.model.*;
import com.nexuswms.repository.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryServiceTest {
    InventoryRepository inv = mock(InventoryRepository.class);
    StockMovementRepository mov = mock(StockMovementRepository.class);
    AuditService audit = mock(AuditService.class);
    NotificationService notes = mock(NotificationService.class);
    InventoryService svc;
    Inventory row;

    @BeforeEach void setUp() {
        svc = new InventoryService(inv, mov, audit, notes);
        Product p = new Product(); p.id = 1L; p.sku = "SKU-1"; p.name = "Box"; p.reorderPoint = 10;
        row = new Inventory(); row.id = 7L; row.product = p; row.quantityOnHand = 12;
        when(inv.findForUpdate(1L)).thenReturn(Optional.of(row));
    }

    @Test void rejectsNegativeResult() {
        BusinessException e = assertThrows(BusinessException.class, () -> svc.adjustStock(1L, -13, MovementReason.SHIP, 1L, null));
        assertTrue(e.getMessage().contains("below zero"));
        verify(mov, never()).save(any()); verify(audit, never()).log(any(), any(), any(), any(), any());
        assertEquals(12, row.quantityOnHand);
    }
    @Test void rejectsZeroDelta() { assertThrows(BusinessException.class, () -> svc.adjustStock(1L, 0, MovementReason.ADJUST, 1L, null)); }
    @Test void updatesQuantityWritesLedgerAndAudit() {
        int after = svc.adjustStock(1L, 8, MovementReason.RECEIVE, 3L, "note", 55L);
        assertEquals(20, after); assertEquals(20, row.quantityOnHand);
        ArgumentCaptor<StockMovement> c = ArgumentCaptor.forClass(StockMovement.class);
        verify(mov).save(c.capture());
        assertEquals(8, c.getValue().delta); assertEquals(MovementReason.RECEIVE, c.getValue().reason);
        assertEquals(55L, c.getValue().refOrderId); assertEquals(3L, c.getValue().actorUserId);
        verify(audit).log(eq("INVENTORY"), eq(7L), eq("ADJUST"), eq(3L), contains("after=20"));
        verify(notes, never()).publish(any(), any());
    }
    @Test void notifiesWhenCrossingReorderPoint() {
        svc.adjustStock(1L, -5, MovementReason.SHIP, 1L, null);
        verify(notes).publish(eq("WARN"), contains("reorder point"));
    }
    @Test void statusBuckets() {
        assertEquals("OUT", InventoryService.status(0, 5)); assertEquals("LOW", InventoryService.status(5, 5)); assertEquals("OK", InventoryService.status(6, 5));
    }

    @Test void directionMustMatchReason() {
        assertThrows(BusinessException.class, () -> svc.adjustStock(1L, -1, MovementReason.RECEIVE, 1L, null));
        assertThrows(BusinessException.class, () -> svc.adjustStock(1L, 1, MovementReason.SHIP, 1L, null));
        assertThrows(BusinessException.class, () -> svc.adjustStock(1L, 1, MovementReason.SHRINK, 1L, null));
        verify(mov, never()).save(any());
    }
    @Test void cannotDropBelowReservedQuantity() {
        row.quantityReserved = 10;
        BusinessException e = assertThrows(BusinessException.class, () -> svc.adjustStock(1L, -5, MovementReason.SHIP, 1L, null));
        assertTrue(e.getMessage().contains("reserved")); assertEquals(12, row.quantityOnHand);
    }
    @Test void inactiveProductIsFrozen() { row.product.active = false; assertThrows(BusinessException.class, () -> svc.adjustStock(1L, 1, MovementReason.ADJUST, 1L, null)); }
    @Test void nullsAndHugeValuesRejected() {
        assertThrows(BusinessException.class, () -> svc.adjustStock(null, 1, MovementReason.ADJUST, 1L, null));
        assertThrows(BusinessException.class, () -> svc.adjustStock(1L, 1, null, 1L, null));
        assertThrows(BusinessException.class, () -> svc.adjustStock(1L, Integer.MAX_VALUE, MovementReason.RECEIVE, 1L, null));
        assertThrows(BusinessException.class, () -> svc.adjustStock(1L, 1, MovementReason.ADJUST, 1L, "x".repeat(501)));
    }
    @Test void overflowIsHandled() {
        row.quantityOnHand = InventoryService.MAX_STOCK;
        assertThrows(BusinessException.class, () -> svc.adjustStock(1L, 5, MovementReason.RECEIVE, 1L, null));
    }
}
