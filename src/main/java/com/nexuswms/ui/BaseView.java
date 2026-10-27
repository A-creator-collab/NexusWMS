package com.nexuswms.ui;

import com.nexuswms.ui.components.*;
import com.nexuswms.ui.util.FxTaskHelper;
import com.nexuswms.ui.util.Icons;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

/** Common view plumbing: page header, busy indicator, background loading, safe mutations, null-safe tables and cards. */
public abstract class BaseView extends BorderPane {
    protected final AppContext app;
    private final ProgressBar busy = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
    private int pending;

    protected BaseView(AppContext app) {
        this.app = app;
        getStyleClass().add("content-area");
        setPadding(new Insets(24, 28, 24, 28));
        busy.getStyleClass().add("progress-thin"); busy.setMaxWidth(Double.MAX_VALUE); busy.setVisible(false);
    }

    protected void setHeader(String title, Node... actions) { setPageHeader(title, "", actions); }

    /** Title + subtitle on the left, actions on the right, thin busy bar underneath. */
    protected void setPageHeader(String title, String subtitle, Node... actions) {
        Label t = new Label(title); t.getStyleClass().add("page-title");
        Label s = new Label(subtitle == null ? "" : subtitle); s.getStyleClass().add("page-subtitle");
        s.setManaged(subtitle != null && !subtitle.isBlank()); s.setVisible(s.isManaged());
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        HBox acts = new HBox(8, actions); acts.setAlignment(Pos.CENTER_RIGHT);
        HBox row = new HBox(12, new VBox(2, t, s), sp, acts); row.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(10, row, busy); box.setPadding(new Insets(0, 0, 16, 0)); setTop(box);
    }

    public abstract void refresh();

    /** Background fetch with busy indicator; no-op while offline. */
    protected <T> void load(Callable<T> work, Consumer<T> ok) {
        if (!app.online()) return;
        busy(true);
        FxTaskHelper.run(work, v -> { busy(false); ok.accept(v); }, e -> { busy(false); FxTaskHelper.error(e); });
    }

    protected <T> void act(Callable<T> work, String okMessage) { act(work, v -> okMessage); }

    /** Background mutation: success toast, then refresh (also refreshes on failure so the screen never shows stale state). */
    protected <T> void act(Callable<T> work, Function<T, String> okMessage) {
        if (!app.online()) return;
        busy(true);
        FxTaskHelper.run(work, v -> { busy(false); FxTaskHelper.info("Done", okMessage.apply(v)); refresh(); },
                e -> { busy(false); FxTaskHelper.error(e); refresh(); });
    }

    private void busy(boolean on) { pending = Math.max(0, pending + (on ? 1 : -1)); busy.setVisible(pending > 0); }

    /** Asks where to save, then builds the rows and writes the CSV on a worker thread. */
    protected void exportCsv(String suggestedName, List<String> header, Callable<List<List<String>>> rows) {
        if (getScene() == null) return;
        FileChooser fc = new FileChooser();
        fc.setTitle("Export CSV"); fc.setInitialFileName(suggestedName);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File f = fc.showSaveDialog(getScene().getWindow());
        if (f == null) return;
        busy(true);
        FxTaskHelper.run(() -> { List<List<String>> r = rows.call(); com.nexuswms.ui.util.CsvExporter.write(f.toPath(), header, r); return r.size(); },
                n -> { busy(false); FxTaskHelper.info("Exported", n + " row(s) saved to " + f.getName()); },
                e -> { busy(false); FxTaskHelper.error(e); });
    }

    // ---- null-safe helpers ----------------------------------------------------------------

    protected static <T> Optional<T> selected(TableView<T> t) { return Optional.ofNullable(t.getSelectionModel().getSelectedItem()); }
    protected static <T> void withSelected(TableView<T> t, Consumer<T> c) { T v = t.getSelectionModel().getSelectedItem(); if (v != null) c.accept(v); }

    private static <T> String safe(Function<T, String> f, T v) {
        try { String s = v == null ? "" : f.apply(v); return s == null ? "" : s; } catch (RuntimeException e) { return ""; }
    }

    protected static <T> TableColumn<T, String> col(String title, Function<T, String> f, double width) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new SimpleStringProperty(safe(f, cd.getValue())));
        c.setPrefWidth(width); c.setSortable(true); return c;
    }

    protected static <T> TableColumn<T, String> badgeCol(String title, Function<T, String> f, double width) {
        TableColumn<T, String> c = col(title, f, width);
        c.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                setText(null);
                setGraphic(empty || s == null || s.isBlank() ? null : new StatusBadge(s, StatusBadge.kindForStatus(s)));
            }
        });
        return c;
    }

    /** Applies the data-table look (alternating rows, hover, fixed row height) and an icon placeholder. */
    protected <T> void styleTable(TableView<T> t, String icon, String title, String text) {
        t.getStyleClass().add("data-table");
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        t.setFixedCellSize(46);
        t.setPlaceholder(app.online() ? EmptyState.of(icon, title, text) : EmptyState.of(Icons.DATABASE, "You are offline", "Reconnect to the database to see this data."));
    }

    protected static VBox card(Node... children) {
        VBox c = new VBox(10, children); c.getStyleClass().add("card"); return c;
    }

    /** Card without padding, used to host a table edge-to-edge. */
    protected static VBox flushCard(Node content) {
        VBox c = new VBox(content); c.getStyleClass().addAll("card", "flush");
        VBox.setVgrow(content, Priority.ALWAYS); return c;
    }

    protected static Label sectionTitle(String t) { Label l = new Label(t); l.getStyleClass().add("section-title"); return l; }

    protected static Button button(String text, String icon, String variant) {
        Button b = new Button(text, icon == null ? null : Icons.of(icon, 13));
        if (variant != null && !variant.isBlank()) b.getStyleClass().add(variant);
        return b;
    }
}
