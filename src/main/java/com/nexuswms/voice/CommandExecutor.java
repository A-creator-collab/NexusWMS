package com.nexuswms.voice;

import com.nexuswms.forecasting.*;
import com.nexuswms.model.*;
import com.nexuswms.repository.*;
import com.nexuswms.service.*;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

/** Executes parsed commands against the real services. Order creation only ever creates a DRAFT. */
@Service
public class CommandExecutor {
    public static final String HELP = "Try: \"show low stock for category dairy\", \"create order for 5 of SKU-1023 from Acme\", "
            + "\"what is inventory value\", \"forecast\".";
    private final InventoryService inventory; private final OrderService orders; private final ProductRepository products;
    private final SupplierRepository suppliers; private final AnalyticsService analytics; private final ForecastService forecasts;

    public CommandExecutor(InventoryService inventory, OrderService orders, ProductRepository products,
                           SupplierRepository suppliers, AnalyticsService analytics, ForecastService forecasts) {
        this.inventory = inventory; this.orders = orders; this.products = products;
        this.suppliers = suppliers; this.analytics = analytics; this.forecasts = forecasts;
    }

    public String execute(ParsedCommand c, Long actor, boolean canMutate) {
        switch (c.type()) {
            case LOW_STOCK: {
                String cat = c.arg("category");
                List<InventoryService.InventoryRow> rows = inventory.lowStock().stream()
                        .filter(r -> cat == null || (r.category() != null && r.category().toLowerCase().contains(cat))).toList();
                if (rows.isEmpty()) return "No low-stock items" + (cat == null ? "." : " in category '" + cat + "'.");
                return rows.size() + " low-stock item(s):\n" + rows.stream()
                        .map(r -> "• " + r.sku() + " " + r.name() + " – available " + r.available() + " (reorder at " + r.reorderPoint() + ")")
                        .collect(Collectors.joining("\n"));
            }
            case INVENTORY_VALUE:
                return "Total inventory value is " + analytics.inventoryValue().toPlainString() + ".";
            case FORECAST: {
                List<ReorderRecommendation> recs = forecasts.recommendations().stream().filter(r -> r.reorderQty() > 0).limit(5).toList();
                if (recs.isEmpty()) return "No reorders are recommended right now.";
                return "Top reorder recommendations:\n" + recs.stream()
                        .map(r -> "• " + r.sku() + " " + r.name() + ": order " + r.reorderQty()).collect(Collectors.joining("\n"));
            }
            case CREATE_ORDER: {
                if (!canMutate) return "Your role is read-only, so I can't create orders.";
                Product p = products.findBySkuIgnoreCase(c.arg("sku")).orElse(null);
                if (p == null) return "I couldn't find SKU " + c.arg("sku") + ".";
                Supplier s = p.supplier;
                if (c.arg("supplier") != null) {
                    String want = c.arg("supplier");
                    s = suppliers.findAll().stream().filter(x -> x.active && x.name.toLowerCase().contains(want.toLowerCase())).findFirst().orElse(null);
                }
                if (s == null) return "I couldn't determine the supplier; say \"from <supplier name>\".";
                int qty;
                try { qty = Integer.parseInt(c.arg("quantity")); } catch (NumberFormatException e) { return "I couldn't read the quantity."; }
                if (qty <= 0 || qty > OrderService.MAX_QTY) return "Quantity must be between 1 and " + OrderService.MAX_QTY + ".";
                if (!p.active) return p.sku + " is deactivated.";
                PurchaseOrder o = orders.createDraft(s.id, List.of(new OrderService.Line(p.id, qty, null)), null, actor);
                return "Draft order " + o.orderNumber + " created: " + c.arg("quantity") + " × " + p.sku + " from " + s.name
                        + ". It is a DRAFT – submit it from the Orders screen.";
            }
            case NAVIGATE: return "Opening " + c.arg("view") + ".";
            case HELP: return HELP;
            default: return "I didn't understand that. " + HELP;
        }
    }
}
