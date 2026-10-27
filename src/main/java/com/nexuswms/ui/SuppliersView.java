package com.nexuswms.ui;

import com.nexuswms.model.*;
import com.nexuswms.service.SupplierService;
import com.nexuswms.ui.components.*;
import com.nexuswms.ui.util.*;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class SuppliersView extends BaseView {
    private final TableView<Supplier> table = new TableView<>();
    private final TableView<PurchaseOrder> history = new TableView<>();
    private final Button add = button("Add supplier", Icons.PLUS, "primary"), edit = button("Edit", Icons.EDIT, null), del = button("Delete", Icons.TRASH, null);

    public SuppliersView(AppContext app) {
        super(app);
        setPageHeader("Suppliers", "Lead times and delivery reliability", add);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        HBox toolbar = new HBox(8, sp, edit, del); toolbar.getStyleClass().add("toolbar");
        table.getColumns().addAll(col("Name", (Supplier s) -> s.name, 190), col("Email", s -> s.contactEmail, 200), col("Phone", s -> s.phone, 120),
                col("Lead time", s -> s.leadTimeDays + " d", 90),
                col("Reliability", s -> s.reliabilityScore == null ? "" : String.format("%.0f%%", s.reliabilityScore.doubleValue() * 100), 100),
                badgeCol("Status", s -> s.active ? "OK" : "CANCELLED", 90));
        history.getColumns().addAll(col("Order #", (PurchaseOrder o) -> o.orderNumber, 160), badgeCol("Status", o -> o.status == null ? "" : o.status.name(), 110),
                col("Expected", o -> Formatters.date(o.expectedDate), 110), col("Total", o -> Formatters.money(o.totalAmount), 110));
        styleTable(table, Icons.SUPPLIERS, "No suppliers yet", "Add a supplier to start creating purchase orders.");
        styleTable(history, Icons.ORDERS, "No supplier selected", "Select a supplier to see its order history.");
        VBox lower = new VBox(8, sectionTitle("Order history"), flushCard(history)); VBox.setVgrow(lower.getChildren().get(1), Priority.ALWAYS);
        SplitPane split = new SplitPane(flushCard(table), lower); split.setOrientation(javafx.geometry.Orientation.VERTICAL); split.setDividerPositions(0.55);
        VBox body = new VBox(12, toolbar, split); VBox.setVgrow(split, Priority.ALWAYS); setCenter(body);

        add.disableProperty().bind(app.readOnly());
        edit.disableProperty().bind(app.readOnly().or(table.getSelectionModel().selectedItemProperty().isNull())); del.disableProperty().bind(edit.disableProperty());
        table.getSelectionModel().selectedItemProperty().addListener((o, a, s) -> {
            if (s == null) history.getItems().clear();
            else load(() -> app.bean(SupplierService.class).history(s.id), l -> history.setItems(FXCollections.observableArrayList(l))); });
        add.setOnAction(e -> SupplierDialog.show(null).ifPresent(f -> act(() -> app.bean(SupplierService.class).save(null, f, app.userId()), "Supplier created")));
        edit.setOnAction(e -> withSelected(table, s -> SupplierDialog.show(app.bean(SupplierService.class).toForm(s))
                .ifPresent(f -> act(() -> app.bean(SupplierService.class).save(s.id, f, app.userId()), "Supplier updated"))));
        del.setOnAction(e -> withSelected(table, s -> {
            if (Dlg.confirm("Delete supplier", "Delete " + s.name + "?\nSuppliers with order history are deactivated instead.\nSuppliers with OPEN orders cannot be removed.", "Delete", true))
                act(() -> app.bean(SupplierService.class).delete(s.id, app.userId()), soft -> soft ? "Supplier deactivated" : "Supplier deleted"); }));
    }

    @Override public void refresh() { load(() -> app.bean(SupplierService.class).listAll(), l -> table.setItems(FXCollections.observableArrayList(l))); }
}
