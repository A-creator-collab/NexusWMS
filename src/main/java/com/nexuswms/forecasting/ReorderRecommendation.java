package com.nexuswms.forecasting;

import java.math.BigDecimal;

/** reorderQty = max(0, forecastDemand + safetyStock - onHand - onOrder). */
public record ReorderRecommendation(Long productId, String sku, String name, Long supplierId, int onHand, int onOrder,
                                    double forecastDemand, int safetyStock, int reorderQty, BigDecimal unitPrice) {}
