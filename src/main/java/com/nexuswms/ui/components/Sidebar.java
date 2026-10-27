package com.nexuswms.ui.components;

import com.nexuswms.ui.util.Icons;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;
import java.util.function.Consumer;

/** Left navigation: brand, grouped icon buttons (with live count badges) and the signed-in user card. */
public class Sidebar extends VBox {
    private record Item(String name, String icon) {}
    private final ToggleGroup group = new ToggleGroup();
    private final Map<String, ToggleButton> buttons = new LinkedHashMap<>();
    private final Map<String, Label> counts = new HashMap<>();

    public Sidebar(String userName, String role, boolean admin, Consumer<String> onSelect) {
        getStyleClass().add("sidebar"); setSpacing(2);
        Label name = new Label("NexusWMS"); name.getStyleClass().add("brand-name");
        Label sub = new Label("Warehouse operations"); sub.getStyleClass().add("brand-sub");
        StackPane chip = new StackPane(Icons.of(Icons.WAREHOUSE, 16)); chip.getStyleClass().add("brand-chip");
        HBox brand = new HBox(10, chip, new VBox(0, name, sub)); brand.getStyleClass().add("brand");
        getChildren().add(brand);
        group("OPERATIONS", onSelect, new Item("Dashboard", Icons.DASHBOARD), new Item("Inventory", Icons.INVENTORY), new Item("Orders", Icons.ORDERS),
                new Item("Suppliers", Icons.SUPPLIERS), new Item("Warehouse Map", Icons.MAP));
        group("INTELLIGENCE", onSelect, new Item("Analytics", Icons.ANALYTICS), new Item("Agents", Icons.AGENTS), new Item("Approvals", Icons.APPROVALS));
        if (admin) group("ADMINISTRATION", onSelect, new Item("Users", Icons.USER), new Item("Audit", Icons.AUDIT), new Item("Settings", Icons.SETTINGS));
        else group("ADMINISTRATION", onSelect, new Item("Audit", Icons.AUDIT), new Item("Settings", Icons.SETTINGS));
        Region grow = new Region(); VBox.setVgrow(grow, Priority.ALWAYS); getChildren().add(grow);
        String initials = userName == null || userName.isBlank() ? "?" : userName.substring(0, Math.min(2, userName.length())).toUpperCase();
        Label av = new Label(initials); StackPane avatar = new StackPane(av); avatar.getStyleClass().add("avatar");
        Label un = new Label(userName == null ? "Offline" : userName); un.getStyleClass().add("user-name");
        Label ur = new Label(role == null ? "no session" : role); ur.getStyleClass().add("user-role");
        HBox card = new HBox(10, avatar, new VBox(0, un, ur)); card.getStyleClass().add("user-card"); getChildren().add(card);
    }

    private void group(String title, Consumer<String> onSelect, Item... items) {
        Label g = new Label(title); g.getStyleClass().add("nav-group"); getChildren().add(g);
        for (Item it : items) {
            ToggleButton b = new ToggleButton(it.name(), Icons.of(it.icon(), 15));
            b.setToggleGroup(group); b.setMaxWidth(Double.MAX_VALUE); b.getStyleClass().add("nav-button");
            Label count = new Label(); count.getStyleClass().add("nav-count"); count.setVisible(false); count.setManaged(false);
            counts.put(it.name(), count);
            Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
            b.setContentDisplay(ContentDisplay.LEFT);
            b.setOnAction(e -> { if (!b.isSelected()) b.setSelected(true); onSelect.accept(it.name()); });
            StackPane wrap = new StackPane(b, count); StackPane.setAlignment(count, Pos.CENTER_RIGHT); StackPane.setMargin(count, new javafx.geometry.Insets(0, 10, 0, 0));
            buttons.put(it.name(), b); getChildren().add(wrap);
        }
    }

    public void select(String name) { ToggleButton b = buttons.get(name); if (b != null) b.setSelected(true); }

    public void setBadge(String name, long n) {
        Label l = counts.get(name); if (l == null) return;
        l.setText(n > 99 ? "99+" : String.valueOf(n)); l.setVisible(n > 0); l.setManaged(n > 0);
    }

    public Collection<String> names() { return buttons.keySet(); }
}
