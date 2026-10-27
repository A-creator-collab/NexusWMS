package com.nexuswms.agents;

import com.nexuswms.forecasting.*;
import com.nexuswms.model.*;
import com.nexuswms.service.SettingsService;
import java.time.LocalDate;
import java.util.List;

public record AgentContext(List<ReorderRecommendation> recommendations, List<Inventory> inventories, List<PurchaseOrder> openOrders,
                           ForecastService forecasts, SettingsService settings, LocalDate today) {}
