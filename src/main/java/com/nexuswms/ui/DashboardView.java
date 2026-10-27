package com.nexuswms.ui;

import com.nexuswms.service.*;
import com.nexuswms.service.AnalyticsService.*;
import com.nexuswms.service.InventoryService.*;
import com.nexuswms.ui.components.*;
import com.nexuswms.ui.util.*;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.List;

public class DashboardView extends BaseView {
    private record Data(Kpis kpis, List<Point> series, List<InventoryRow> low, List<MovementRow> moves) {}
    private final KpiCard skus = new KpiCard("Total SKUs", Icons.INVENTORY, "accent"), low = new KpiCard("Low-stock items", Icons.WARNING, "warning"),
            appr = new KpiCard("Pending approvals", Icons.APPROVALS, "info"), pos = new KpiCard("Open purchase orders", Icons.ORDERS, "success");
    private final LineChart<String, Number> chart;
    private final VBox attention = new VBox();
    private final TableView<MovementRow> moves = new TableView<>();

    public DashboardView(AppContext app) {
        super(app);
        setPageHeader("Dashboard", "Live overview of stock, orders and pending decisions");
        NumberAxis y = new NumberAxis(); y.setForceZeroInRange(false);
        chart = new LineChart<>(new CategoryAxis(), y);
        chart.setLegendVisible(false); chart.setAnimated(false); chart.setCreateSymbols(false); chart.setMinHeight(280);

        HBox kpis = new HBox(16, skus, low, appr, pos);
        VBox chartCard = card(sectionTitle("Inventory value"), subtitle("Last 12 weeks, valued at current unit prices"), chart);
        VBox.setVgrow(chart, Priority.ALWAYS); HBox.setHgrow(chartCard, Priority.ALWAYS);
        ScrollPane attScroll = new ScrollPane(attention); attScroll.setFitToWidth(true); attScroll.setPrefHeight(280);
        VBox attCard = card(sectionTitle("Attention needed"), subtitle("At or below the reorder point"), attScroll);
        attCard.setMinWidth(340); attCard.setPrefWidth(360);
        HBox mid = new HBox(16, chartCard, attCard);

        moves.getColumns().addAll(col("When", (MovementRow m) -> Formatters.dateTime(m.at()), 140), col("SKU", MovementRow::sku, 90),
                col("Product", MovementRow::product, 220), col("Change", m -> Formatters.signed(m.delta()), 80),
                badgeCol("Type", MovementRow::reason, 110), col("Note", MovementRow::note, 260));
        styleTable(moves, Icons.HISTORY, "No movements yet", "Stock changes will appear here as they happen.");
        moves.setPrefHeight(46 * 8 + 42);
        VBox table = new VBox(10, new VBox(2, sectionTitle("Recent stock movements")), flushCard(moves));
        VBox.setVgrow(moves, Priority.ALWAYS);

        VBox page = new VBox(18, kpis, mid, table);
        ScrollPane sp = new ScrollPane(page); sp.setFitToWidth(true); sp.setStyle("-fx-background-color: transparent;");
        setCenter(sp);
    }

    private static Label subtitle(String t) { Label l = new Label(t); l.getStyleClass().add("section-sub"); return l; }

    private Node attentionRow(InventoryRow r) {
        VBox texts = new VBox(1);
        Label t = new Label(r.sku() + " · " + r.name()); t.getStyleClass().add("attention-title");
        Label s = new Label(r.available() + " available · reorder at " + r.reorderPoint()); s.getStyleClass().add("attention-sub");
        texts.getChildren().addAll(t, s);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        HBox row = new HBox(10, texts, sp, new StatusBadge(r.status(), StatusBadge.kindForStatus(r.status())));
        row.getStyleClass().add("attention-row"); row.setAlignment(Pos.CENTER_LEFT); return row;
    }

    @Override public void refresh() {
        if (!app.online()) { attention.getChildren().setAll(EmptyState.of(Icons.DATABASE, "You are offline", "Reconnect to see live data.")); return; }
        load(() -> new Data(app.bean(AnalyticsService.class).kpis(), app.bean(AnalyticsService.class).valueSeries(12),
                app.bean(InventoryService.class).lowStock(), app.bean(InventoryService.class).recent(15)), d -> {
            skus.setValue(String.valueOf(d.kpis().skus()));
            low.setValue(String.valueOf(d.kpis().lowStock())); low.setHint(d.kpis().lowStock() == 0 ? "All items healthy" : "Needs replenishment");
            appr.setValue(String.valueOf(d.kpis().pendingApprovals())); appr.setHint(d.kpis().pendingApprovals() == 0 ? "Nothing waiting" : "Awaiting your decision");
            pos.setValue(String.valueOf(d.kpis().openPos()));
            XYChart.Series<String, Number> s = new XYChart.Series<>();
            d.series().forEach(p -> s.getData().add(new XYChart.Data<>(p.label(), p.value())));
            chart.getData().setAll(List.of(s));
            if (d.low().isEmpty()) attention.getChildren().setAll(EmptyState.of(Icons.CHECK, "All stocked up", "No product is at or below its reorder point."));
            else attention.getChildren().setAll(d.low().stream().limit(25).map(this::attentionRow).toList());
            moves.setItems(FXCollections.observableArrayList(d.moves()));
        });
    }
}
