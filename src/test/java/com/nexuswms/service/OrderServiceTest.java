package com.nexuswms.service;

import com.nexuswms.model.*;
import com.nexuswms.repository.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderServiceTest {
    PurchaseOrderRepository orders = mock(PurchaseOrderRepository.class);
    SupplierRepository suppliers = mock(SupplierRepository.class);
    ProductRepository products = mock(ProductRepository.class);
    InventoryService inventory = mock(InventoryService.class);
    SupplierService supplierService = mock(SupplierService.class);
    AuditService audit = mock(AuditService.class);
    NotificationService notes = mock(NotificationService.class);
    OrderService svc;
    Supplier supplier; Product p1, p2; PurchaseOrder order;

    @BeforeEach void setUp() {
        svc = new OrderService(orders, suppliers, products, inventory, supplierService, audit, notes);
        when(orders.save(any())).thenAnswer(i -> { PurchaseOrder o = i.getArgument(0); if (o.id == null) o.id = 100L; return o; });
        supplier = new Supplier(); supplier.id = 2L; supplier.name = "Acme"; supplier.leadTimeDays = 5;
        p1 = new Product(); p1.id = 11L; p1.sku = "A"; p1.unitPrice = new BigDecimal("2.50");
        p2 = new Product(); p2.id = 12L; p2.sku = "B"; p2.unitPrice = new BigDecimal("1.00");
        when(suppliers.findById(2L)).thenReturn(Optional.of(supplier));
        when(products.findById(11L)).thenReturn(Optional.of(p1)); when(products.findById(12L)).thenReturn(Optional.of(p2));
        order = new PurchaseOrder(); order.id = 100L; order.orderNumber = "PO-T"; order.supplier = supplier; order.status = OrderStatus.CONFIRMED;
        order.items.add(item(1L, p1, 10)); order.items.add(item(2L, p2, 5));
        when(orders.findById(100L)).thenReturn(Optional.of(order));
        when(orders.findForUpdate(100L)).thenReturn(Optional.of(order));
    }
    OrderItem item(Long id, Product p, int q) { OrderItem i = new OrderItem(); i.id = id; i.order = order; i.product = p; i.quantityOrdered = q; return i; }

    @Test void createDraftComputesTotalAndDefaultsDate() {
        PurchaseOrder o = svc.createDraft(2L, List.of(new OrderService.Line(11L, 4, null), new OrderService.Line(12L, 3, new BigDecimal("2.00"))), null, 1L);
        assertEquals(OrderStatus.DRAFT, o.status); assertEquals(new BigDecimal("16.00"), o.totalAmount);
        assertEquals(LocalDate.now().plusDays(5), o.expectedDate);
        verify(audit).log(eq("ORDER"), any(), eq("CREATE"), eq(1L), anyString());
    }
    @Test void createDraftNeedsLines() { assertThrows(BusinessException.class, () -> svc.createDraft(2L, List.of(), null, 1L)); }
    @Test void invalidTransitionRejected() { assertThrows(BusinessException.class, () -> svc.submit(100L, 1L)); }
    @Test void partialReceiveMovesToReceivingAndAdjustsStock() {
        PurchaseOrder o = svc.receive(100L, Map.of(1L, 10), 7L);
        assertEquals(OrderStatus.RECEIVING, o.status);
        verify(inventory).adjustStock(eq(11L), eq(10), eq(MovementReason.RECEIVE), eq(7L), anyString(), eq(100L));
        verify(inventory, never()).adjustStock(eq(12L), anyInt(), any(), any(), any(), any());
        verify(supplierService, never()).recomputeReliability(any());
    }
    @Test void fullReceiveCompletesAndRescoresSupplier() {
        PurchaseOrder o = svc.receive(100L, Map.of(1L, 10, 2L, 5), 7L);
        assertEquals(OrderStatus.RECEIVED, o.status);
        verify(supplierService).recomputeReliability(2L);
    }
    @Test void cannotOverReceive() { assertThrows(BusinessException.class, () -> svc.receive(100L, Map.of(2L, 6), 7L)); verify(inventory, never()).adjustStock(any(), anyInt(), any(), any(), any(), any()); }
    @Test void receiveRequiresConfirmedStatus() { order.status = OrderStatus.DRAFT; assertThrows(BusinessException.class, () -> svc.receive(100L, Map.of(1L, 1), 7L)); }
    @Test void cancelBlockedAfterReceiving() { order.status = OrderStatus.RECEIVING; assertThrows(BusinessException.class, () -> svc.cancel(100L, 1L)); }

    @Test void inactiveSupplierRejected() { supplier.active = false; assertThrows(BusinessException.class, () -> svc.createDraft(2L, List.of(new OrderService.Line(11L, 1, null)), null, 1L)); }
    @Test void negativeAndZeroQuantitiesRejected() {
        assertThrows(BusinessException.class, () -> svc.createDraft(2L, List.of(new OrderService.Line(11L, -5, null)), null, 1L));
        assertThrows(BusinessException.class, () -> svc.createDraft(2L, List.of(new OrderService.Line(11L, 0, null)), null, 1L));
    }
    @Test void negativeCostAndPastDateRejected() {
        assertThrows(BusinessException.class, () -> svc.createDraft(2L, List.of(new OrderService.Line(11L, 1, new BigDecimal("-1"))), null, 1L));
        assertThrows(BusinessException.class, () -> svc.createDraft(2L, List.of(new OrderService.Line(11L, 1, null)), LocalDate.now().minusDays(1), 1L));
    }
    @Test void excessiveQuantityRejected() { assertThrows(BusinessException.class, () -> svc.createDraft(2L, List.of(new OrderService.Line(11L, OrderService.MAX_QTY + 1, null)), null, 1L)); }
    @Test void inactiveProductRejected() { p1.active = false; assertThrows(BusinessException.class, () -> svc.createDraft(2L, List.of(new OrderService.Line(11L, 1, null)), null, 1L)); }
    @Test void duplicateProductsAreMergedIntoOneLine() {
        PurchaseOrder o = svc.createDraft(2L, List.of(new OrderService.Line(11L, 4, null), new OrderService.Line(11L, 6, null)), null, 1L);
        assertEquals(1, o.items.size()); assertEquals(10, o.items.get(0).quantityOrdered);
    }
    @Test void receiveRejectsUnknownLineAndNegativeQuantity() {
        assertThrows(BusinessException.class, () -> svc.receive(100L, Map.of(99L, 1), 7L));
        assertThrows(BusinessException.class, () -> svc.receive(100L, Map.of(1L, -1), 7L));
        verify(inventory, never()).adjustStock(any(), anyInt(), any(), any(), any(), any());
    }
    @Test void submitBlockedWhenSupplierDeactivated() { order.status = OrderStatus.DRAFT; supplier.active = false; assertThrows(BusinessException.class, () -> svc.submit(100L, 1L)); }
}
