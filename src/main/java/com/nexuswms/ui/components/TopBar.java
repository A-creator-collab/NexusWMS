package com.nexuswms.ui.components;

import com.nexuswms.ui.util.Icons;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.function.Consumer;

/** Top navigation bar: breadcrumb, global search (Ctrl+K), microphone, notifications, theme toggle, database status. */
public class TopBar extends HBox {
    private final Label current = new Label("Dashboard");
    private final TextField search = new TextField();
    private final Label status = new Label("Checking…");
    private final Label unread = new Label();
    private final Button mic = new Button(), bell = new Button(), theme = new Button();

    public TopBar(Consumer<String> onSearch, Runnable onMic, Runnable onBell, Runnable onTheme, boolean dark) {
        getStyleClass().add("topbar");
        Label root = new Label("NexusWMS"); root.getStyleClass().add("crumb");
        Label sep = new Label("/"); sep.getStyleClass().add("crumb"); current.getStyleClass().add("crumb-current");
        search.setPromptText("Search products, SKUs…");
        Label kbd = new Label("Ctrl K"); kbd.getStyleClass().add("kbd");
        HBox box = new HBox(8, Icons.of(Icons.SEARCH, 13), search, kbd); box.getStyleClass().add("search-box"); box.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(search, Priority.ALWAYS);
        search.setOnAction(e -> { String q = search.getText() == null ? "" : search.getText().trim(); if (!q.isEmpty()) onSearch.accept(q); });
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        mic.setGraphic(Icons.of(Icons.MIC)); mic.getStyleClass().add("icon-button"); mic.setTooltip(new Tooltip("Voice command"));
        mic.setOnAction(e -> onMic.run());
        bell.setGraphic(Icons.of(Icons.BELL)); bell.getStyleClass().add("icon-button"); bell.setTooltip(new Tooltip("Notifications"));
        bell.setOnAction(e -> onBell.run());
        unread.getStyleClass().add("bell-badge"); unread.setVisible(false); unread.setManaged(false); unread.setMouseTransparent(true);
        StackPane bellBox = new StackPane(bell, unread); StackPane.setAlignment(unread, Pos.TOP_RIGHT);
        theme.getStyleClass().add("icon-button"); theme.setTooltip(new Tooltip("Toggle light / dark"));
        setThemeIcon(dark);
        theme.setOnAction(e -> onTheme.run());
        status.getStyleClass().addAll("status-pill", "checking");
        getChildren().addAll(root, sep, current, spacer, box, mic, bellBox, theme, status);
        setAlignment(Pos.CENTER_LEFT);
    }

    public void setPage(String name) { current.setText(name); }
    public void setThemeIcon(boolean dark) { theme.setGraphic(Icons.of(dark ? Icons.SUN : Icons.MOON)); }
    public void focusSearch() { search.requestFocus(); search.selectAll(); }

    public void setDbStatus(boolean up) {
        status.setText(up ? "Database online" : "Database offline");
        status.setGraphic(Icons.of(Icons.DATABASE, 11));
        status.getStyleClass().removeAll("ok", "down", "checking"); status.getStyleClass().add(up ? "ok" : "down");
    }

    public void setUnread(int n) { unread.setText(n > 99 ? "99+" : String.valueOf(n)); unread.setVisible(n > 0); unread.setManaged(n > 0); }
    public void setListening(boolean on) { mic.getStyleClass().remove("listening"); if (on) mic.getStyleClass().add("listening"); }
}
