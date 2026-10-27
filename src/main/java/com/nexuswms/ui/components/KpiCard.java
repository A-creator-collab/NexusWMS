package com.nexuswms.ui.components;

import com.nexuswms.ui.util.Icons;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

public class KpiCard extends HBox {
    private final Label value = new Label("–"), hint = new Label();
    public KpiCard(String title, String icon, String variant) {
        getStyleClass().add("kpi-card");
        Label t = new Label(title); t.getStyleClass().add("kpi-title");
        value.getStyleClass().add("kpi-value"); hint.getStyleClass().add("kpi-hint");
        VBox texts = new VBox(1, t, value, hint);
        getChildren().addAll(Icons.chip(icon, variant, 18), texts);
        setPadding(new Insets(16, 18, 16, 18)); HBox.setHgrow(this, Priority.ALWAYS); setMaxWidth(Double.MAX_VALUE);
    }
    public void setValue(String v) { value.setText(v == null ? "–" : v); }
    public void setHint(String h) { hint.setText(h == null ? "" : h); hint.setManaged(h != null && !h.isBlank()); hint.setVisible(h != null && !h.isBlank()); }
}
