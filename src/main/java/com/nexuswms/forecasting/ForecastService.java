package com.nexuswms.forecasting;

import com.nexuswms.model.*;
import com.nexuswms.repository.*;
import com.nexuswms.service.*;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

@Service
public class ForecastService {
    private static final Logger log = LoggerFactory.getLogger(ForecastService.class);
    public static final int HISTORY_DAYS = 90;
    static final double ALPHA = 0.3;
    static final int RETENTION_DAYS = 90;
    static final List<OrderStatus> ON_ORDER = List.of(OrderStatus.SUBMITTED, OrderStatus.CONFIRMED, OrderStatus.RECEIVING);

    public record DemandStats(double[] history, double mean, double std, double level) {}
    public record ChartData(List<Double> history, double level, double std) {}

    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final ForecastRepository forecasts;
    private final InventoryRepository inventory;
    private final OrderItemRepository items;
    private final AuditService audit;
    private final NotificationService notes;
    private final ExponentialSmoothingForecaster smoothing = new ExponentialSmoothingForecaster();

    public ForecastService(ProductRepository products, StockMovementRepository movements, ForecastRepository forecasts,
                           InventoryRepository inventory, OrderItemRepository items, AuditService audit, NotificationService notes) {
        this.products = products; this.movements = movements; this.forecasts = forecasts;
        this.inventory = inventory; this.items = items; this.audit = audit; this.notes = notes;
    }

    /** Daily outbound demand (SHIP movements) for the last 90 days, oldest first. */
    @Transactional(readOnly = true)
    public DemandStats stats(Product p) {
        double[] daily = new double[HISTORY_DAYS];
        LocalDate today = LocalDate.now();
        for (StockMovement m : movements.findByProductIdAndCreatedAtAfter(p.id, today.minusDays(HISTORY_DAYS - 1L).atStartOfDay())) {
            if (m.reason != MovementReason.SHIP || m.delta >= 0) continue;
            int idx = HISTORY_DAYS - 1 - (int) java.time.temporal.ChronoUnit.DAYS.between(m.createdAt.toLocalDate(), today);
            if (idx >= 0 && idx < HISTORY_DAYS) daily[idx] += -m.delta;
        }
        double mean = Arrays.stream(daily).average().orElse(0);
        double var = Arrays.stream(daily).map(d -> (d - mean) * (d - mean)).sum() / Math.max(1, HISTORY_DAYS - 1);
        return new DemandStats(daily, mean, Math.sqrt(var), smoothing.forecast(daily, ALPHA));
    }

    @Transactional(readOnly = true)
    public int onOrder(Long productId) {
        int n = 0;
        for (OrderItem i : items.findByProductIdAndOrderStatusIn(productId, ON_ORDER)) n += i.remaining();
        return n;
    }

    static int leadTime(Product p) { return p.supplier == null ? 7 : Math.max(1, p.supplier.leadTimeDays); }

    @Transactional(readOnly = true)
    public List<ReorderRecommendation> recommendations() {
        List<ReorderRecommendation> out = new ArrayList<>();
        for (Inventory i : inventory.findAllActive()) {
            Product p = i.product;
            DemandStats s = stats(p);
            int lead = leadTime(p);
            double demand = s.level() * lead;
            int calc = SafetyStockCalculator.safetyStock(s.std(), lead, SafetyStockCalculator.DEFAULT_SERVICE_LEVEL);
            int safety = Math.max(p.safetyStock, calc);
            int onOrder = onOrder(p.id);
            int qty = (int) Math.max(0, Math.ceil(demand + safety - i.quantityOnHand - onOrder));
            out.add(new ReorderRecommendation(p.id, p.sku, p.name, p.supplier == null || !p.supplier.active ? null : p.supplier.id, i.quantityOnHand,
                    onOrder, round(demand), safety, qty, p.unitPrice));
        }
        out.sort(Comparator.comparingInt(ReorderRecommendation::reorderQty).reversed());
        return out;
    }

    /** Writes one forecast row per active product and returns how many were written. Also checks demand deviation (> 2 sigma). */
    @Transactional
    public int runForecast(Long actor) {
        int n = 0;
        for (Product p : products.findByActiveTrueOrderBySku()) {
            DemandStats s = stats(p);
            int lead = leadTime(p);
            Forecast f = new Forecast();
            f.product = p; f.forecastDate = LocalDate.now().plusDays(lead);
            f.predictedDemand = BigDecimal.valueOf(s.level() * lead).setScale(2, RoundingMode.HALF_UP);
            f.method = "EXP_SMOOTH"; f.confidence = BigDecimal.valueOf(SafetyStockCalculator.DEFAULT_SERVICE_LEVEL);
            forecasts.save(f);
            n++;
            double recent = Arrays.stream(s.history()).skip(HISTORY_DAYS - 7L).average().orElse(0);
            if (s.std() > 0 && Math.abs(recent - s.mean()) > 2 * s.std() / Math.sqrt(7))
                AfterCommit.run(() -> notes.publish("WARN", "Demand for " + p.sku + " deviates >2σ from its 90-day average."));
        }
        long pruned = forecasts.deleteByCreatedAtBefore(LocalDateTime.now().minusDays(RETENTION_DAYS));
        audit.log("FORECAST", null, "RUN", actor, "products=" + n + " pruned=" + pruned);
        return n;
    }

    @Transactional(readOnly = true)
    public ChartData chartData(Long productId) {
        Product p = products.findById(productId).orElseThrow(() -> new BusinessException("Product not found."));
        DemandStats s = stats(p);
        List<Double> h = new ArrayList<>();
        for (int i = HISTORY_DAYS - 30; i < HISTORY_DAYS; i++) h.add(s.history()[i]);
        return new ChartData(h, s.level(), s.std());
    }

    @Scheduled(cron = "${nexus.forecast.cron:0 0 2 * * *}")
    public void nightly() {
        try { runForecast(null); } catch (RuntimeException e) { log.error("Nightly forecast failed", e); }
    }

    private static double round(double v) { return Math.round(v * 100.0) / 100.0; }
}
