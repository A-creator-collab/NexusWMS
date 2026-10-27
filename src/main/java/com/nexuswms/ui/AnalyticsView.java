package com.nexuswms.ui;

import com.nexuswms.forecasting.*;
import com.nexuswms.forecasting.ForecastService.ChartData;
import com.nexuswms.service.AnalyticsService;
import com.nexuswms.service.AnalyticsService.Point;
import com.nexuswms.ui.util.FxTaskHelper;
import javafx.collections.FXCollections;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.List;

public class AnalyticsView extends BaseView {
    private record Overview(List<Point> value, List<Point> category, List<Point> orders, List<Point> suppliers, List<ReorderRecommendation> recs) {}
    private final LineChart<String, Number> valueChart = new LineChart<>(new CategoryAxis(), new NumberAxis());
    private final PieChart pie = new PieChart();
    private final BarChart<String, Number> ordersChart = new BarChart<>(new CategoryAxis(), new NumberAxis());
    private final BarChart<String, Number> supplierChart = new BarChart<>(new CategoryAxis(), new NumberAxis());
    private final TableView<ReorderRecommendation> recs = new TableView<>();
    private final LineChart<String, Number> forecastChart = new LineChart<>(new CategoryAxis(), new NumberAxis());
    private final Button run = new Button("Run forecast now");

    public AnalyticsView(AppContext app) {
        super(app);
        setPageHeader("Analytics", "Trends, supplier performance and demand forecasts", run);
        run.disableProperty().bind(app.readOnly());
        valueChart.setTitle("Inventory value over time"); pie.setTitle("Stock by category"); ordersChart.setTitle("Order volume by week");
        supplierChart.setTitle("Supplier on-time rate (%)");
        for (XYChart<String, Number> c : List.of(valueChart, ordersChart, supplierChart)) { c.setLegendVisible(false); c.setAnimated(false); }
        GridPane grid = new GridPane(); grid.setHgap(12); grid.setVgap(12);
        grid.add(valueChart, 0, 0); grid.add(pie, 1, 0); grid.add(ordersChart, 0, 1); grid.add(supplierChart, 1, 1);
        for (javafx.scene.Node n : List.<javafx.scene.Node>of(valueChart, pie, ordersChart, supplierChart)) { GridPane.setHgrow(n, Priority.ALWAYS); GridPane.setVgrow(n, Priority.ALWAYS); }
        recs.getColumns().addAll(col("SKU", (ReorderRecommendation r) -> r.sku(), 80), col("Product", r -> r.name(), 160), col("On hand", r -> "" + r.onHand(), 65),
                col("On order", r -> "" + r.onOrder(), 70), col("Forecast", r -> String.format("%.1f", r.forecastDemand()), 70),
                col("Safety", r -> "" + r.safetyStock(), 60), col("Reorder qty", r -> "" + r.reorderQty(), 85));
        styleTable(recs, com.nexuswms.ui.util.Icons.ANALYTICS, "No recommendations", "Run the forecast to see what to reorder.");
        for (javafx.scene.Node n : List.<javafx.scene.Node>of(valueChart, pie, ordersChart, supplierChart, forecastChart)) n.getStyleClass().add("card");
        forecastChart.setTitle("Select a product: 30-day demand, forecast and ±1.645σ band"); forecastChart.setCreateSymbols(false); forecastChart.setAnimated(false);
        SplitPane fc = new SplitPane(flushCard(recs), forecastChart); fc.setDividerPositions(0.45);
        TabPane tabs = new TabPane(new Tab("Overview", grid), new Tab("Forecast", fc)); tabs.getTabs().forEach(t -> t.setClosable(false)); setCenter(tabs);
        recs.getSelectionModel().selectedItemProperty().addListener((o, a, r) -> { if (r != null && r.productId() != null) loadChart(r); });
        run.setOnAction(e -> act(() -> app.bean(ForecastService.class).runForecast(app.userId()), n -> "Forecast written for " + n + " products"));
    }

    private void loadChart(ReorderRecommendation r) {
        if (r == null || r.productId() == null) return;
        load(() -> app.bean(ForecastService.class).chartData(r.productId()), cd -> {
            XYChart.Series<String, Number> actual = new XYChart.Series<>(), fore = new XYChart.Series<>(), up = new XYChart.Series<>(), lo = new XYChart.Series<>();
            actual.setName("Actual"); fore.setName("Forecast"); up.setName("Upper"); lo.setName("Lower");
            for (int i = 0; i < cd.history().size(); i++) actual.getData().add(new XYChart.Data<>("-" + (cd.history().size() - 1 - i), cd.history().get(i)));
            double band = 1.645 * cd.std();
            for (int d = 1; d <= 7; d++) {
                String k = "+" + d;
                fore.getData().add(new XYChart.Data<>(k, cd.level()));
                up.getData().add(new XYChart.Data<>(k, cd.level() + band));
                lo.getData().add(new XYChart.Data<>(k, Math.max(0, cd.level() - band)));
            }
            forecastChart.setTitle(r.sku() + " – daily demand, forecast and confidence band");
            forecastChart.getData().setAll(List.of(actual, fore, up, lo));
        });
    }

    private static void fill(XYChart<String, Number> c, List<Point> pts) {
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        pts.forEach(p -> s.getData().add(new XYChart.Data<>(p.label(), p.value()))); c.getData().setAll(List.of(s));
    }

    @Override public void refresh() {
        load(() -> { var a = app.bean(AnalyticsService.class);
            return new Overview(a.valueSeries(12), a.stockByCategory(), a.orderVolume(8), a.supplierOnTime(), app.bean(ForecastService.class).recommendations()); }, o -> {
            fill(valueChart, o.value()); fill(ordersChart, o.orders()); fill(supplierChart, o.suppliers());
            pie.setData(FXCollections.observableArrayList(o.category().stream().map(p -> new PieChart.Data(p.label(), p.value())).toList()));
            recs.setItems(FXCollections.observableArrayList(o.recs()));
        });
    }
}
