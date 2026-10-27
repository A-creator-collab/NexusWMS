package com.nexuswms.service;

import com.nexuswms.agents.ApprovalStatus;
import com.nexuswms.model.*;
import com.nexuswms.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Repository-driven aggregates for the dashboard and analytics charts. */
@Service
public class AnalyticsService {
    public record Kpis(long skus, long lowStock, long pendingApprovals, long openPos) {}
    public record Point(String label, double value) {}
    /** Orders that are not finished: block deleting their supplier. */
    public static final List<OrderStatus> OPEN = List.of(OrderStatus.DRAFT, OrderStatus.SUBMITTED, OrderStatus.CONFIRMED, OrderStatus.RECEIVING);
    /** Orders that already left the building (stock is expected): block deleting/deactivating their products. */
    public static final List<OrderStatus> OPEN_AND_IN_FLIGHT = OPEN;

    private final ProductRepository products;
    private final InventoryRepository inventory;
    private final PurchaseOrderRepository orders;
    private final StockMovementRepository movements;
    private final ApprovalRequestRepository approvals;
    private final SupplierRepository suppliers;

    public AnalyticsService(ProductRepository products, InventoryRepository inventory, PurchaseOrderRepository orders,
                            StockMovementRepository movements, ApprovalRequestRepository approvals, SupplierRepository suppliers) {
        this.products = products; this.inventory = inventory; this.orders = orders;
        this.movements = movements; this.approvals = approvals; this.suppliers = suppliers;
    }

    @Transactional(readOnly = true)
    public Kpis kpis() {
        return new Kpis(products.countByActiveTrue(), inventory.findLowStock().size(),
                approvals.countByStatus(ApprovalStatus.PENDING), orders.countByStatusIn(OPEN));
    }

    private double currentValue() {
        double v = 0;
        for (Inventory i : inventory.findAllActive()) v += i.product.unitPrice.doubleValue() * i.quantityOnHand;
        return v;
    }

    /** Rewinds today's value through the ledger (valued at current unit price) to estimate each past week-end value. */
    @Transactional(readOnly = true)
    public List<Point> valueSeries(int weeks) {
        LocalDateTime now = LocalDateTime.now();
        List<StockMovement> since = movements.findByCreatedAtAfter(now.minusWeeks(weeks).minusDays(1));
        double current = currentValue();
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd MMM");
        List<Point> pts = new ArrayList<>();
        for (int k = weeks - 1; k >= 0; k--) {
            LocalDateTime t = now.minusWeeks(k);
            double v = current;
            for (StockMovement m : since) if (m.createdAt.isAfter(t)) v -= m.delta * m.product.unitPrice.doubleValue();
            pts.add(new Point(t.format(f), Math.max(0, v)));
        }
        return pts;
    }

    @Transactional(readOnly = true)
    public List<Point> stockByCategory() {
        Map<String, Double> m = new TreeMap<>();
        for (Inventory i : inventory.findAllActive())
            m.merge(i.product.category == null ? "Uncategorised" : i.product.category, (double) i.quantityOnHand, Double::sum);
        return m.entrySet().stream().map(e -> new Point(e.getKey(), e.getValue())).toList();
    }

    @Transactional(readOnly = true)
    public List<Point> orderVolume(int weeks) {
        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
        Map<LocalDate, Integer> counts = new LinkedHashMap<>();
        for (int k = weeks - 1; k >= 0; k--) counts.put(monday.minusWeeks(k), 0);
        for (PurchaseOrder o : orders.findAll()) {
            LocalDate w = o.createdAt.toLocalDate().with(DayOfWeek.MONDAY);
            counts.computeIfPresent(w, (d, c) -> c + 1);
        }
        return counts.entrySet().stream().map(e -> new Point(e.getKey().format(DateTimeFormatter.ofPattern("dd MMM")), e.getValue())).toList();
    }

    @Transactional(readOnly = true)
    public List<Point> supplierOnTime() {
        return suppliers.findByActiveTrueOrderByName().stream().map(s -> new Point(s.name, s.reliabilityScore.doubleValue() * 100)).toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal inventoryValue() { return BigDecimal.valueOf(currentValue()).setScale(2, java.math.RoundingMode.HALF_UP); }
}
