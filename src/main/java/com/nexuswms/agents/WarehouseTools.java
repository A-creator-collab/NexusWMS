package com.nexuswms.agents;

import com.nexuswms.forecasting.*;
import com.nexuswms.model.OrderStatus;
import com.nexuswms.service.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import java.util.stream.Collectors;

/** READ-ONLY tools exposed to the LLM. None of them mutate warehouse data. */
@Component
public class WarehouseTools {
    private final InventoryService inventory; private final OrderService orders;
    private final ForecastService forecasts; private final AnalyticsService analytics;

    public WarehouseTools(InventoryService inventory, OrderService orders, ForecastService forecasts, AnalyticsService analytics) {
        this.inventory = inventory; this.orders = orders; this.forecasts = forecasts; this.analytics = analytics;
    }

    @Tool(description = "Search current inventory by SKU, name or category. Returns up to 15 rows with on-hand, available and status.")
    public String queryInventory(@ToolParam(description = "search text, may be empty") String search) {
        return inventory.list(search == null ? "" : search, 0, 15).getContent().stream()
                .map(r -> r.sku() + " | " + r.name() + " | cat=" + r.category() + " | onHand=" + r.onHand() + " | available=" + r.available()
                        + " | reorderPoint=" + r.reorderPoint() + " | " + r.status()).collect(Collectors.joining("\n"));
    }

    @Tool(description = "List purchase orders, optionally filtered by status (DRAFT, SUBMITTED, CONFIRMED, RECEIVING, RECEIVED, CANCELLED).")
    public String queryOrders(@ToolParam(description = "status or empty for all") String status) {
        OrderStatus st = null;
        try { if (status != null && !status.isBlank()) st = OrderStatus.valueOf(status.trim().toUpperCase()); } catch (IllegalArgumentException e) { return "Unknown status."; }
        return orders.list(st).stream().limit(20).map(o -> o.orderNumber + " | " + o.supplier.name + " | " + o.status + " | total " + o.totalAmount)
                .collect(Collectors.joining("\n"));
    }

    @Tool(description = "Compute current forecast-based reorder recommendations (read-only; does not create orders).")
    public String runForecast() {
        return forecasts.recommendations().stream().filter(r -> r.reorderQty() > 0).limit(10)
                .map(r -> r.sku() + " " + r.name() + ": reorder " + r.reorderQty() + " (forecast " + r.forecastDemand() + ", safety " + r.safetyStock() + ")")
                .collect(Collectors.joining("\n"));
    }

    @Tool(description = "Summarize the 12-week inventory value trend and KPIs.")
    public String summarizeTrends() {
        var s = analytics.valueSeries(12); var k = analytics.kpis();
        double first = s.get(0).value(), last = s.get(s.size() - 1).value();
        return String.format("Inventory value moved from %.0f to %.0f over 12 weeks. SKUs=%d, low-stock=%d, open POs=%d, pending approvals=%d.",
                first, last, k.skus(), k.lowStock(), k.openPos(), k.pendingApprovals());
    }
}
