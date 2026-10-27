package com.nexuswms.ui;

import com.nexuswms.agents.ApprovalService;
import com.nexuswms.config.HealthService;
import com.nexuswms.service.NotificationService;
import com.nexuswms.ui.components.*;
import com.nexuswms.ui.util.*;
import javafx.animation.FadeTransition;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.controlsfx.control.Notifications;
import org.slf4j.*;
import java.util.*;

/** Application shell: sidebar, top bar and animated content area. Renders in a degraded offline state when MySQL is unavailable. */
public class MainWindow {
    private static final Logger log = LoggerFactory.getLogger(MainWindow.class);
    private record Pulse(boolean up, long pendingApprovals) {}

    private final AppContext app;
    private final BorderPane root = new BorderPane();
    private final StackPane content = new StackPane();
    private final Map<String, BaseView> views = new LinkedHashMap<>();
    private final TopBar topBar;
    private final Sidebar sidebar;
    private ScheduledService<Pulse> health;

    public MainWindow(AppContext app, Runnable onReconnect) {
        this.app = app;
        root.getStyleClass().add("app-shell");
        views.put("Dashboard", new DashboardView(app)); views.put("Inventory", new InventoryView(app)); views.put("Orders", new OrdersView(app));
        views.put("Suppliers", new SuppliersView(app)); views.put("Warehouse Map", new WarehouseMapView(app)); views.put("Analytics", new AnalyticsView(app));
        views.put("Agents", new AgentsView(app)); views.put("Approvals", new ApprovalView(app)); views.put("Audit", new AuditView(app));
        boolean admin = app.online() && app.session().role() == com.nexuswms.model.Role.ADMIN;
        if (admin) views.put("Users", new UsersView(app));
        views.put("Settings", new SettingsView(app));

        String user = app.online() ? app.session().username() : null;
        String role = app.online() && app.session().role() != null ? app.session().role().name() : null;
        sidebar = new Sidebar(user, role, admin, this::show);
        topBar = new TopBar(this::globalSearch, () -> openVoice(true), this::showNotifications, this::toggleTheme, ThemeManager.isDark());
        VBox top = new VBox(topBar);
        if (!app.online()) top.getChildren().add(offlineBanner(onReconnect));
        VBox centre = new VBox(top, content); VBox.setVgrow(content, Priority.ALWAYS);
        content.getStyleClass().add("content-area");
        root.setLeft(sidebar); root.setCenter(centre);
        topBar.setDbStatus(app.online());
        show("Dashboard");
        if (app.online()) { wireNotifications(); startHealthCheck(); }
    }

    public Parent root() { return root; }

    public void installShortcuts(Scene scene) {
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN), topBar::focusSearch);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.M, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN), () -> openVoice(true));
    }

    public void shutdown() { if (health != null) health.cancel(); }

    private void show(String name) {
        BaseView v = views.get(name);
        if (v == null) { log.warn("Unknown view '{}'", name); return; }
        topBar.setPage(name); sidebar.select(name);
        content.getChildren().setAll(v);
        FadeTransition ft = new FadeTransition(Duration.millis(160), v); ft.setFromValue(0.0); ft.setToValue(1.0); ft.play();
        v.refresh();
    }

    private void globalSearch(String q) {
        BaseView v = views.get("Inventory");
        if (v instanceof InventoryView iv) { iv.setQuery(q); show("Inventory"); }
    }

    private HBox offlineBanner(Runnable onReconnect) {
        Label l = new Label("Offline mode – the database is unreachable. Data is hidden and changes are disabled."); l.getStyleClass().add("offline-text");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Button retry = new Button("Retry connection", Icons.of(Icons.REFRESH, 12)); retry.getStyleClass().add("primary"); retry.setOnAction(e -> onReconnect.run());
        Button details = new Button("Details"); details.setOnAction(e -> Dlg.error("Database unavailable", app.offlineReason == null ? "No details available." : app.offlineReason));
        HBox b = new HBox(10, Icons.of(Icons.WARNING, 14), l, sp, details, retry); b.getStyleClass().add("offline-banner"); return b;
    }

    private void wireNotifications() {
        NotificationService ns = app.bean(NotificationService.class);
        ns.addListener(n -> FxTaskHelper.ui(() -> {
            Notifications b = Notifications.create().title(n.level()).text(n.message()).hideAfter(Duration.seconds(6));
            if ("WARN".equals(n.level())) b.showWarning(); else b.showInformation();
            topBar.setUnread(ns.unread());
        }));
    }

    private void showNotifications() {
        if (!app.online()) { Dlg.info("Notifications", "You are offline."); return; }
        NotificationService ns = app.bean(NotificationService.class);
        StringBuilder sb = new StringBuilder();
        for (NotificationService.Note n : ns.recent()) sb.append(Formatters.dateTime(n.at())).append("  [").append(n.level()).append("] ").append(n.message()).append('\n');
        ns.markRead(); topBar.setUnread(0);
        Dlg.text("Notifications", sb.length() == 0 ? "No notifications yet." : sb.toString());
    }

    private void openVoice(boolean autoListen) { VoiceCommandDialog.show(app, autoListen, this::show, topBar::setListening); }

    private void toggleTheme() { ThemeManager.toggle(); topBar.setThemeIcon(ThemeManager.isDark()); }

    private void startHealthCheck() {
        health = new ScheduledService<>() {
            @Override protected Task<Pulse> createTask() {
                return new Task<>() {
                    @Override protected Pulse call() {
                        boolean up = app.bean(HealthService.class).ping();
                        long pending = 0;
                        if (up) { try { pending = app.bean(ApprovalService.class).pendingCount(); } catch (RuntimeException ignored) { /* badge is best-effort */ } }
                        return new Pulse(up, pending);
                    }
                };
            }
        };
        health.setPeriod(Duration.seconds(30)); health.setRestartOnFailure(true);
        health.setOnSucceeded(e -> { Pulse p = health.getValue(); if (p == null) return;
            app.dbUp.set(p.up()); topBar.setDbStatus(p.up()); sidebar.setBadge("Approvals", p.pendingApprovals()); });
        health.setOnFailed(e -> { app.dbUp.set(false); topBar.setDbStatus(false); });
        health.start();
    }
}
