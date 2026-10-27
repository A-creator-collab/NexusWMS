package com.nexuswms.ui.components;

import com.nexuswms.ui.util.Icons;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

public final class EmptyState {
    private EmptyState() {}
    public static VBox of(String icon, String title, String text) {
        StackPane chip = new StackPane(Icons.of(icon, 22)); chip.getStyleClass().add("empty-chip");
        Label t = new Label(title); t.getStyleClass().add("empty-title");
        Label m = new Label(text); m.getStyleClass().add("empty-text"); m.setWrapText(true); m.setMaxWidth(320);
        VBox v = new VBox(8, chip, t, m); v.getStyleClass().add("empty-state"); return v;
    }
}
