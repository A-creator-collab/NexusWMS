package com.nexuswms.service;

import com.nexuswms.model.*;
import com.nexuswms.repository.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductServiceTest {
    ProductRepository products = mock(ProductRepository.class);
    InventoryRepository inventory = mock(InventoryRepository.class);
    SupplierRepository suppliers = mock(SupplierRepository.class);
    StockMovementRepository movements = mock(StockMovementRepository.class);
    OrderItemRepository items = mock(OrderItemRepository.class);
    InventoryService invSvc = mock(InventoryService.class);
    AuditService audit = mock(AuditService.class);
    ProductService svc;

    @BeforeEach void setUp() {
        svc = new ProductService(products, inventory, suppliers, movements, items, invSvc, audit);
        when(products.save(any())).thenAnswer(i -> { Product p = i.getArgument(0); p.id = 9L; return p; });
    }
    ProductForm form(String sku) {
        ProductForm f = new ProductForm(); f.sku = sku; f.name = "Thing"; f.unitPrice = BigDecimal.ONE; f.initialQuantity = 4; f.bin = "A-01"; return f;
    }

    @Test void duplicateSkuRejected() {
        when(products.existsBySkuIgnoreCase("DUP")).thenReturn(true);
        assertThrows(BusinessException.class, () -> svc.create(form("DUP"), 1L));
        verify(products, never()).save(any());
    }
    @Test void createSavesInventoryInitialStockAndAudit() {
        Product p = svc.create(form(" NEW "), 1L);
        assertEquals("NEW", p.sku);
        verify(inventory).save(any(Inventory.class));
        verify(invSvc).adjustStock(eq(9L), eq(4), eq(MovementReason.ADJUST), eq(1L), anyString());
        verify(audit).log(eq("PRODUCT"), eq(9L), eq("CREATE"), eq(1L), anyString());
    }
    @Test void deleteWithHistoryIsSoft() {
        Product p = new Product(); p.id = 5L; p.sku = "X"; p.active = true;
        when(products.findById(5L)).thenReturn(Optional.of(p));
        when(movements.existsByProductId(5L)).thenReturn(true);
        assertTrue(svc.delete(5L, 1L));
        assertFalse(p.active); verify(products, never()).delete(any());
        verify(audit).log(eq("PRODUCT"), eq(5L), eq("SOFT_DELETE"), eq(1L), anyString());
    }
    @Test void deleteWithoutHistoryIsHard() {
        Product p = new Product(); p.id = 6L; p.sku = "Y";
        when(products.findById(6L)).thenReturn(Optional.of(p));
        assertFalse(svc.delete(6L, 1L));
        verify(products).delete(p); verify(audit).log(eq("PRODUCT"), eq(6L), eq("DELETE"), eq(1L), anyString());
    }

    @Test void invalidFormRejectedByServiceEvenWithoutUi() {
        ProductForm f = form("OK"); f.unitPrice = new BigDecimal("-1");
        assertThrows(BusinessException.class, () -> svc.create(f, 1L));
        ProductForm g = form("OK"); g.name = " ";
        assertThrows(BusinessException.class, () -> svc.create(g, 1L));
        assertThrows(BusinessException.class, () -> svc.create(null, 1L));
        verify(products, never()).save(any());
    }
    @Test void inactiveSupplierCannotBeAssigned() {
        Supplier s = new Supplier(); s.id = 3L; s.name = "Old"; s.active = false;
        when(suppliers.findById(3L)).thenReturn(Optional.of(s));
        ProductForm f = form("S1"); f.supplierId = 3L;
        assertThrows(BusinessException.class, () -> svc.create(f, 1L));
    }
    @Test void productOnOpenOrderCannotBeDeleted() {
        Product p = new Product(); p.id = 8L; p.sku = "Z"; when(products.findById(8L)).thenReturn(Optional.of(p));
        when(items.existsByProductIdAndOrderStatusIn(eq(8L), any())).thenReturn(true);
        assertThrows(BusinessException.class, () -> svc.delete(8L, 1L));
        assertTrue(p.active);
    }
}
