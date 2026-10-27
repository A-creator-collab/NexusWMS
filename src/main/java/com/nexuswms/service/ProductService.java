package com.nexuswms.service;

import com.nexuswms.model.*;
import com.nexuswms.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Locale;

@Service
public class ProductService {
    private final ProductRepository products;
    private final InventoryRepository inventory;
    private final SupplierRepository suppliers;
    private final StockMovementRepository movements;
    private final OrderItemRepository items;
    private final InventoryService inventoryService;
    private final AuditService audit;

    public ProductService(ProductRepository products, InventoryRepository inventory, SupplierRepository suppliers,
                          StockMovementRepository movements, OrderItemRepository items, InventoryService inventoryService, AuditService audit) {
        this.products = products; this.inventory = inventory; this.suppliers = suppliers;
        this.movements = movements; this.items = items; this.inventoryService = inventoryService; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Product> listActive() { return products.findByActiveTrueOrderBySku(); }

    @Transactional(readOnly = true)
    public ProductForm getForm(Long id) {
        Product p = load(id);
        ProductForm f = new ProductForm();
        f.sku = p.sku; f.name = p.name; f.category = p.category; f.description = p.description; f.unitPrice = p.unitPrice;
        f.reorderPoint = p.reorderPoint; f.safetyStock = p.safetyStock; f.supplierId = p.supplier == null ? null : p.supplier.id;
        inventory.findByProductId(id).ifPresent(i -> { f.bin = i.binLocation; f.initialQuantity = i.quantityOnHand; });
        return f;
    }

    @Transactional
    public Product create(ProductForm f, Long actor) {
        Validate.form(f);
        String sku = normalizeSku(f.sku);
        if (products.existsBySkuIgnoreCase(sku)) throw new BusinessException("SKU '" + sku + "' already exists.");
        Supplier s = resolveSupplier(f.supplierId);
        Product p = new Product();
        apply(p, f, s);
        p.sku = sku;
        p = products.save(p);
        Inventory i = new Inventory();
        i.product = p; i.binLocation = blankToNull(f.bin);
        inventory.save(i);
        if (f.initialQuantity > 0) inventoryService.adjustStock(p.id, f.initialQuantity, MovementReason.ADJUST, actor, "Initial stock");
        audit.log("PRODUCT", p.id, "CREATE", actor, "sku=" + p.sku + " name=" + p.name);
        return p;
    }

    @Transactional
    public Product update(Long id, ProductForm f, Long actor) {
        Validate.form(f);
        Product p = load(id);
        if (!p.active) throw new BusinessException(p.sku + " is deactivated and cannot be edited.");
        String sku = normalizeSku(f.sku);
        if (!sku.equalsIgnoreCase(p.sku) && products.existsBySkuIgnoreCase(sku)) throw new BusinessException("SKU '" + sku + "' already exists.");
        apply(p, f, resolveSupplier(f.supplierId));
        p.sku = sku;
        products.save(p);
        inventory.findByProductId(id).ifPresent(i -> { i.binLocation = blankToNull(f.bin); inventory.save(i); });
        audit.log("PRODUCT", id, "UPDATE", actor, "sku=" + p.sku);
        return p;
    }

    /**
     * @return true if the product was soft-deleted (active=false) because history references it.
     * Products on an open purchase order can be neither deleted nor deactivated.
     */
    @Transactional
    public boolean delete(Long id, Long actor) {
        Product p = load(id);
        if (items.existsByProductIdAndOrderStatusIn(id, AnalyticsService.OPEN_AND_IN_FLIGHT))
            throw new BusinessException(p.sku + " is on an open purchase order. Receive or cancel that order first.");
        if (movements.existsByProductId(id) || items.existsByProductId(id)) {
            p.active = false;
            products.save(p);
            audit.log("PRODUCT", id, "SOFT_DELETE", actor, "sku=" + p.sku + " (has history)");
            return true;
        }
        inventory.findByProductId(id).ifPresent(inventory::delete);
        products.delete(p);
        audit.log("PRODUCT", id, "DELETE", actor, "sku=" + p.sku);
        return false;
    }

    private Product load(Long id) {
        if (id == null) throw new BusinessException("No product selected.");
        return products.findById(id).orElseThrow(() -> new BusinessException("Product not found."));
    }

    private Supplier resolveSupplier(Long supplierId) {
        if (supplierId == null) return null;
        Supplier s = suppliers.findById(supplierId).orElseThrow(() -> new BusinessException("Supplier not found."));
        if (!s.active) throw new BusinessException("Supplier '" + s.name + "' is deactivated and cannot be assigned.");
        return s;
    }

    private void apply(Product p, ProductForm f, Supplier s) {
        p.name = f.name.trim(); p.category = blankToNull(f.category); p.description = blankToNull(f.description);
        p.unitPrice = f.unitPrice.setScale(2, java.math.RoundingMode.HALF_UP); p.reorderPoint = f.reorderPoint; p.safetyStock = f.safetyStock;
        p.supplier = s;
    }

    private static String normalizeSku(String sku) { return sku.trim().toUpperCase(Locale.ROOT); }
    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
