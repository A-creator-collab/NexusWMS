package com.nexuswms.ui.components;

import com.nexuswms.ui.util.Icons;
import com.nexuswms.ui.util.ThemeManager;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;

/**
 * Gives every dialog the application look: borderless window centred on the main window, custom draggable header
 * (icon + title + subtitle + close button), themed body and footer, primary action highlighted.
 */
public final class DialogStyler {
    private static Window owner;
    private static String css;
    private DialogStyler() {}

    public static void setOwner(Window w) { owner = w; }

    public static String css() {
        if (css == null) css = DialogStyler.class.getResource("/css/styles.css").toExternalForm();
        return css;
    }

    /** Call AFTER the dialog's button types have been added. */
    public static void style(Dialog<?> d, String title, String subtitle, String icon) {
        DialogPane pane = d.getDialogPane();
        if (!pane.getStylesheets().contains(css())) pane.getStylesheets().add(css());
        if (!pane.getStyleClass().contains("nx-dialog")) pane.getStyleClass().add("nx-dialog");
        ThemeManager.decorate(pane);
        d.setTitle(title); d.setHeaderText(null); d.setGraphic(null);
        pane.setHeader(header(d, title, subtitle, icon));
        if (owner != null && owner.isShowing() && d.getOwner() == null) d.initOwner(owner);       // dialogs centre on the main window
        try { d.initStyle(StageStyle.UNDECORATED); } catch (IllegalStateException ignored) { /* already shown */ }
        d.setResizable(false);
        for (ButtonType bt : pane.getButtonTypes()) {
            Button b = (Button) pane.lookupButton(bt);
            if (b == null) continue;
            if (bt.getButtonData().isDefaultButton()) b.getStyleClass().add("primary");
            else if (bt.getButtonData().isCancelButton()) b.getStyleClass().add("ghost");
        }
    }

    /** Marks the confirm button of a destructive dialog red. */
    public static void danger(Dialog<?> d, ButtonType bt) {
        Button b = (Button) d.getDialogPane().lookupButton(bt);
        if (b != null) { b.getStyleClass().remove("primary"); b.getStyleClass().add("danger"); }
    }

    private static Node header(Dialog<?> d, String title, String subtitle, String icon) {
        Label t = new Label(title); t.getStyleClass().add("nx-dialog-title");
        VBox texts = new VBox(2, t);
        if (subtitle != null && !subtitle.isBlank()) { Label s = new Label(subtitle); s.getStyleClass().add("nx-dialog-subtitle"); s.setWrapText(true); texts.getChildren().add(s); }
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Button close = new Button(null, Icons.of(Icons.CLOSE, 13));
        close.getStyleClass().addAll("icon-button", "nx-dialog-close"); close.setFocusTraversable(false); close.setOnAction(e -> d.close());
        HBox h = new HBox(12, Icons.chip(icon == null ? Icons.INFO : icon, "accent", 16), texts, spacer, close);
        h.getStyleClass().add("nx-dialog-header");
        double[] off = new double[2];                                         // drag the borderless window by its header
        h.setOnMousePressed(e -> { Window w = h.getScene().getWindow(); off[0] = w.getX() - e.getScreenX(); off[1] = w.getY() - e.getScreenY(); });
        h.setOnMouseDragged(e -> { Window w = h.getScene().getWindow(); w.setX(e.getScreenX() + off[0]); w.setY(e.getScreenY() + off[1]); });
        return h;
    }
}
