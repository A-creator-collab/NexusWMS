package com.nexuswms.agents;

import com.nexuswms.forecasting.SafetyStockCalculator;
import com.nexuswms.forecasting.ForecastService.DemandStats;
import com.nexuswms.model.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;

/**
 * Flags products whose probability of stocking out during the supplier lead time exceeds the configured threshold.
 * P(stock-out) = 1 - Phi((available - mu_L) / sigma_L), with mu_L = mean daily demand * L and sigma_L = std * sqrt(L).
 */
@Component @Order(1)
public class RiskAgent implements Agent {
    @Override public String name() { return "RiskAgent"; }
    @Override public String settingsKey() { return "risk"; }

    @Override
    public List<ProposedAction> propose(AgentContext ctx) {
        double threshold = ctx.settings().riskThreshold();
        List<ProposedAction> out = new ArrayList<>();
        for (Inventory i : ctx.inventories()) {
            Product p = i.product;
            if (p.supplier == null) continue;
            DemandStats s = ctx.forecasts().stats(p);
            int lead = Math.max(1, p.supplier.leadTimeDays);
            double mu = s.mean() * lead, sigma = Math.max(1e-6, s.std() * Math.sqrt(lead));
            if (mu <= 0) continue;
            double prob = 1 - SafetyStockCalculator.normalCdf((i.available() - mu) / sigma);
            if (prob <= threshold) continue;
            int qty = (int) Math.max(1, Math.ceil(mu + SafetyStockCalculator.zScore(0.95) * sigma - i.available()));
            String why = String.format("Stock-out probability %.0f%% over the %d-day lead time exceeds the %.0f%% threshold "
                    + "(available %d vs expected demand %.1f). Proposing an expedited order of %d.", prob * 100, lead, threshold * 100, i.available(), mu, qty);
            out.add(new ProposedAction(name(), AgentAction.EXPEDITE_ORDER, p.id, p.supplier.id, null, qty,
                    p.unitPrice.multiply(BigDecimal.valueOf(qty)), null, why));
        }
        return out;
    }
}
