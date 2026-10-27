package com.nexuswms.service;

import com.nexuswms.model.*;
import com.nexuswms.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrderService {
    public static final int MAX_LINES = 200, MAX_QTY = 1_000_000;
    static final BigDecimal MAX_UNIT_COST = new BigDecimal("9999999.99"), MAX_TOTAL = new BigDecimal("9999999999.99");

    public record Line(Long productId, int quantity, BigDecimal unitCost) {}

    private final PurchaseOrderRepository orders;
    private final SupplierRepository suppliers;
    private final ProductRepository products;
    private final InventoryService inventory;
    private final SupplierService supplierService;
    private final AuditService audit;
    private final NotificationService notes;

    public OrderService(PurchaseOrderRepository orders, SupplierRepository suppliers, ProductRepository products,
                        InventoryService inventory, SupplierService supplierService, AuditService audit, NotificationService notes) {
        this.orders = orders; this.suppliers = suppliers; this.products = products;
        this.inventory = inventory; this.supplierService = supplierService; this.audit = audit; this.notes = notes;
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrder> list(OrderStatus status) {
        return status == null ? orders.findAllByOrderByCreatedAtDescIdDesc() : orders.findByStatusOrderByCreatedAtDescIdDesc(status);
    }

    @Transactional(readOnly = true)
    public PurchaseOrder get(Long id) { return orders.findById(Objects.requireNonNull(id)).orElseThrow(() -> new BusinessException("Order not found.")); }

    @Transactional
    public PurchaseOrder createDraft(Long supplierId, List<Line> lines, LocalDate expected, Long actor) {
        if (supplierId == null) throw new BusinessException("Choose a supplier.");
        if (lines == null || lines.isEmpty()) throw new BusinessException("An order needs at least one line.");
        if (lines.size() > MAX_LINES) throw new BusinessException("An order may have at most " + MAX_LINES + " lines.");
        Supplier s = suppliers.findById(supplierId).orElseThrow(() -> new BusinessException("Supplier not found."));
        if (!s.active) throw new BusinessException("Supplier '" + s.name + "' is deactivated and cannot receive new orders.");
        if (expected != null && expected.isBefore(LocalDate.now())) throw new BusinessException("The expected date cannot be in the past.");

        Map<Long, Line> merged = new LinkedHashMap<>();            // duplicate products are combined into one line
        for (Line l : lines) {
            if (l == null || l.productId() == null) throw new BusinessException("Every line needs a product.");
            if (l.quantity() <= 0) throw new BusinessException("Line quantities must be positive.");
            if (l.unitCost() != null && (l.unitCost().signum() < 0 || l.unitCost().compareTo(MAX_UNIT_COST) > 0))
                throw new BusinessException("Unit cost must be between 0 and " + MAX_UNIT_COST + ".");
            Line prev = merged.get(l.productId());
            long q = (long) l.quantity() + (prev == null ? 0 : prev.quantity());
            if (q > MAX_QTY) throw new BusinessException("Quantity per product may not exceed " + MAX_QTY + ".");
            merged.put(l.productId(), new Line(l.productId(), (int) q, prev != null ? prev.unitCost() : l.unitCost()));
        }

        PurchaseOrder o = new PurchaseOrder();
        o.supplier = s; o.createdBy = actor; o.status = OrderStatus.DRAFT;
        o.expectedDate = expected != null ? expected : LocalDate.now().plusDays(s.leadTimeDays);
        o.orderNumber = nextNumber();
        BigDecimal total = BigDecimal.ZERO;
        for (Line l : merged.values()) {
            Product p = products.findById(l.productId()).orElseThrow(() -> new BusinessException("Product not found."));
            if (!p.active) throw new BusinessException(p.sku + " is deactivated and cannot be ordered.");
            OrderItem it = new OrderItem();
            it.order = o; it.product = p; it.quantityOrdered = l.quantity();
            it.unitCost = (l.unitCost() != null ? l.unitCost() : p.unitPrice).setScale(2, RoundingMode.HALF_UP);
            o.items.add(it);
            total = total.add(it.unitCost.multiply(BigDecimal.valueOf(it.quantityOrdered)));
        }
        if (total.compareTo(MAX_TOTAL) > 0) throw new BusinessException("The order total is too large.");
        o.totalAmount = total;
        PurchaseOrder saved = orders.save(o);
        audit.log("ORDER", saved.id, "CREATE", actor, saved.orderNumber + " supplier=" + s.name + " total=" + total);
        return saved;
    }

    @Transactional public PurchaseOrder submit(Long id, Long actor) { return move(id, OrderStatus.DRAFT, OrderStatus.SUBMITTED, actor); }
    @Transactional public PurchaseOrder confirm(Long id, Long actor) { return move(id, OrderStatus.SUBMITTED, OrderStatus.CONFIRMED, actor); }

    @Transactional
    public PurchaseOrder cancel(Long id, Long actor) {
        PurchaseOrder o = lock(id);
        if (o.status != OrderStatus.DRAFT && o.status != OrderStatus.SUBMITTED && o.status != OrderStatus.CONFIRMED)
            throw new BusinessException("An order in status " + o.status + " cannot be cancelled.");
        return transition(o, OrderStatus.CANCELLED, actor);
    }

    /** Per-line (partial) receiving. Each received line increments inventory and appends a RECEIVE movement, atomically. */
    @Transactional
    public PurchaseOrder receive(Long id, Map<Long, Integer> qtyByItemId, Long actor) {
        PurchaseOrder o = lock(id);
        if (o.status != OrderStatus.CONFIRMED && o.status != OrderStatus.RECEIVING)
            throw new BusinessException("Only CONFIRMED or RECEIVING orders can be received (this one is " + o.status + ").");
        if (qtyByItemId == null) throw new BusinessException("Nothing to receive.");
        Set<Long> known = o.items.stream().map(i -> i.id).collect(Collectors.toSet());
        for (Map.Entry<Long, Integer> e : qtyByItemId.entrySet()) {
            if (e.getKey() == null || !known.contains(e.getKey())) throw new BusinessException("Line " + e.getKey() + " does not belong to this order.");
            if (e.getValue() == null || e.getValue() < 0) throw new BusinessException("Received quantities cannot be negative.");
        }
        boolean any = false;
        for (OrderItem it : o.items) {
            int q = qtyByItemId.getOrDefault(it.id, 0);
            if (q == 0) continue;
            if (q > it.remaining()) throw new BusinessException("Cannot receive " + q + " of " + it.product.sku + "; only " + it.remaining() + " outstanding.");
            it.quantityReceived += q;
            inventory.adjustStock(it.product.id, q, MovementReason.RECEIVE, actor, "Received on " + o.orderNumber, o.id);
            any = true;
        }
        if (!any) throw new BusinessException("Enter a quantity for at least one line.");
        boolean complete = o.items.stream().allMatch(i -> i.remaining() == 0);
        PurchaseOrder saved = transition(o, complete ? OrderStatus.RECEIVED : OrderStatus.RECEIVING, actor);
        if (complete) supplierService.recomputeReliability(o.supplier.id);
        return saved;
    }

    private PurchaseOrder move(Long id, OrderStatus from, OrderStatus to, Long actor) {
        PurchaseOrder o = lock(id);
        if (o.status != from) throw new BusinessException("Order " + o.orderNumber + " is " + o.status + "; expected " + from + ".");
        if (to == OrderStatus.SUBMITTED && !o.supplier.active)
            throw new BusinessException("Supplier '" + o.supplier.name + "' is deactivated; the order cannot be submitted.");
        return transition(o, to, actor);
    }

    private PurchaseOrder transition(PurchaseOrder o, OrderStatus to, Long actor) {
        OrderStatus from = o.status;
        o.status = to;
        PurchaseOrder saved = orders.save(o);
        audit.log("ORDER", o.id, "STATUS", actor, o.orderNumber + " " + from + " -> " + to);
        String msg = "Order " + o.orderNumber + " is now " + to + ".";
        AfterCommit.run(() -> notes.publish("INFO", msg));
        return saved;
    }

    /** Loads with a pessimistic write lock so concurrent transitions/receipts serialize. */
    private PurchaseOrder lock(Long id) {
        if (id == null) throw new BusinessException("No order selected.");
        return orders.findForUpdate(id).orElseThrow(() -> new BusinessException("Order not found."));
    }

    private String nextNumber() {
        String n;
        do { n = "PO-" + LocalDate.now().toString().replace("-", "") + "-" + String.format("%04X", new Random().nextInt(0x10000)); }
        while (orders.existsByOrderNumber(n));
        return n;
    }
}
