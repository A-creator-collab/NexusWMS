package com.nexuswms.ui;

import com.nexuswms.service.InventoryService;
import com.nexuswms.service.InventoryService.BinCell;
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.control.*;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.*;
import java.util.*;

/** Bin map: one rectangle per bin, coloured by fill ratio (green >50%, amber 20-50%, red <20%, grey empty). Zoom with buttons or Ctrl+scroll; pan by dragging. */
public class WarehouseMapView extends BaseView {
    private static final double W = 96, H = 78, GAP = 10;
    private final Group map = new Group();
    private final Group wrap = new Group(map);
    private final ScrollPane scroll = new ScrollPane(wrap);
    private final Label detail = new Label("Click a bin for details");
    private double scale = 1.0;

    public WarehouseMapView(AppContext app) {
        super(app);
        Button in = new Button("+"), out = new Button("−"), reset = new Button("Reset");
        in.setOnAction(e -> zoom(1.2)); out.setOnAction(e -> zoom(1 / 1.2)); reset.setOnAction(e -> { scale = 1; apply(); });
        setPageHeader("Warehouse Map", "Bin fill levels – Ctrl+scroll to zoom, drag to pan", out, in, reset);
        scroll.setPannable(true); scroll.setStyle("-fx-background-color: transparent;");
        scroll.addEventFilter(ScrollEvent.SCROLL, e -> { if (e.isControlDown()) { zoom(e.getDeltaY() > 0 ? 1.1 : 1 / 1.1); e.consume(); } });
        HBox legend = new HBox(14, key(Color.web("#2da44e"), "> 50%"), key(Color.web("#d4a72c"), "20–50%"), key(Color.web("#cf222e"), "< 20%"), key(Color.GRAY, "empty"));
        detail.setWrapText(true); detail.setPadding(new Insets(8)); detail.getStyleClass().addAll("card", "map-detail"); detail.setPrefWidth(250); detail.setAlignment(javafx.geometry.Pos.TOP_LEFT);
        VBox left = new VBox(8, legend, scroll); VBox.setVgrow(scroll, Priority.ALWAYS);
        HBox body = new HBox(12, left, detail); HBox.setHgrow(left, Priority.ALWAYS); setCenter(body);
    }

    private HBox key(Color c, String t) { Rectangle r = new Rectangle(14, 14, c); return new HBox(5, r, new Label(t)); }
    private void zoom(double f) { scale = Math.max(0.4, Math.min(3.0, scale * f)); apply(); }
    private void apply() { map.setScaleX(scale); map.setScaleY(scale); }

    static Color colorFor(BinCell b) {
        if (b.onHand() <= 0) return Color.GRAY;
        return b.ratio() > 0.5 ? Color.web("#2da44e") : b.ratio() >= 0.2 ? Color.web("#d4a72c") : Color.web("#cf222e");
    }

    private void draw(List<BinCell> bins) {
        map.getChildren().clear();
        Map<String, Integer> zoneRow = new TreeMap<>();
        for (BinCell b : bins) zoneRow.putIfAbsent(zone(b.bin()), 0);
        int r = 0; for (String z : zoneRow.keySet()) zoneRow.put(z, r++);
        Map<String, Integer> nextCol = new HashMap<>();
        List<BinCell> sorted = new ArrayList<>(bins); sorted.sort(Comparator.comparing(BinCell::bin));
        for (BinCell b : sorted) {
            String z = zone(b.bin()); int col = nextCol.merge(z, 1, Integer::sum) - 1;
            double px = col * (W + GAP), py = zoneRow.get(z) * (H + GAP);
            Rectangle rect = new Rectangle(px, py, W, H); rect.setArcWidth(10); rect.setArcHeight(10);
            rect.setFill(colorFor(b)); rect.setStroke(Color.web("#00000040"));
            Text t = new Text(px + 8, py + 20, b.bin() + "\n" + b.sku() + "\n" + b.onHand() + " / " + b.capacity());
            t.setFill(Color.WHITE); t.setFont(Font.font("System", FontWeight.BOLD, 12)); t.setMouseTransparent(true);
            rect.setOnMouseClicked(e -> detail.setText("Bin " + b.bin() + "\n" + b.sku() + " – " + b.product() + "\nOn hand: " + b.onHand()
                    + "\nCapacity: " + b.capacity() + " (4× reorder point)\nFill: " + Math.round(b.ratio() * 100) + "%"));
            map.getChildren().addAll(rect, t);
        }
        apply();
    }

    private static String zone(String bin) { return bin == null || bin.length() < 1 || bin.equals("-") ? "?" : bin.substring(0, 1).toUpperCase(); }

    @Override public void refresh() { load(() -> app.bean(InventoryService.class).mapBins(), this::draw); }
}
