package com.nexuswms.ui;

import com.nexuswms.model.*;
import com.nexuswms.service.*;
import com.nexuswms.ui.components.*;
import com.nexuswms.ui.util.*;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;

public class OrdersView extends BaseView {
    private final TableView<PurchaseOrder> table = new TableView<>();
    private final TableView<OrderItem> items = new TableView<>();
    private final ChoiceBox<String> filter = new ChoiceBox<>();
    private final Label detailTitle = sectionTitle("Order lines");
    private final Button create = button("New order", Icons.PLUS, "primary"), submit = button("Submit", Icons.SEND, null),
            confirm = button("Confirm", Icons.CHECK, null), receive = button("Receive…", Icons.TRUCK_LOADING, null), cancel = button("Cancel order", Icons.BAN, null);

    public OrdersView(AppContext app) {
        super(app);
        setPageHeader("Orders", "Purchase orders from draft to receipt", create);
        filter.getItems().add("ALL"); for (OrderStatus s : OrderStatus.values()) filter.getItems().add(s.name()); filter.setValue("ALL");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Label fl = new Label("Status"); fl.getStyleClass().add("field-label");
        HBox toolbar = new HBox(8, fl, filter, sp, submit, confirm, receive, cancel); toolbar.getStyleClass().add("toolbar");

        table.getColumns().addAll(col("Order #", (PurchaseOrder o) -> o.orderNumber, 160), col("Supplier", o -> o.supplier == null ? "" : o.supplier.name, 170),
                badgeCol("Status", o -> o.status == null ? "" : o.status.name(), 110), col("Expected", o -> Formatters.date(o.expectedDate), 100),
                col("Total", o -> Formatters.money(o.totalAmount), 110), col("Created", o -> Formatters.dateTime(o.createdAt), 140));
        items.getColumns().addAll(col("SKU", (OrderItem i) -> i.product.sku, 100), col("Product", i -> i.product.name, 240),
                col("Ordered", i -> "" + i.quantityOrdered, 80), col("Received", i -> "" + i.quantityReceived, 80), col("Unit cost", i -> Formatters.money(i.unitCost), 100));
        styleTable(table, Icons.ORDERS, "No orders", "Create a purchase order, or let the Replenishment agent propose one.");
        styleTable(items, Icons.INBOX, "No order selected", "Select an order to see its lines.");
        VBox lines = new VBox(8, detailTitle, flushCard(items)); VBox.setVgrow(lines.getChildren().get(1), Priority.ALWAYS);
        SplitPane split = new SplitPane(flushCard(table), lines); split.setOrientation(javafx.geometry.Orientation.VERTICAL); split.setDividerPositions(0.55);
        VBox body = new VBox(12, toolbar, split); VBox.setVgrow(split, Priority.ALWAYS); setCenter(body);

        ObservableValue<PurchaseOrder> sel = table.getSelectionModel().selectedItemProperty();
        create.disableProperty().bind(app.readOnly());
        bind(submit, sel, OrderStatus.DRAFT); bind(confirm, sel, OrderStatus.SUBMITTED);
        bind(receive, sel, OrderStatus.CONFIRMED, OrderStatus.RECEIVING); bind(cancel, sel, OrderStatus.DRAFT, OrderStatus.SUBMITTED, OrderStatus.CONFIRMED);
        sel.addListener((o, a, n) -> {
            items.setItems(n == null || n.items == null ? FXCollections.observableArrayList() : FXCollections.observableArrayList(n.items));
            detailTitle.setText(n == null ? "Order lines" : n.orderNumber + " · lines"); });
        filter.valueProperty().addListener((o, a, b) -> refresh());

        create.setOnAction(e -> load(() -> new Object[]{app.bean(SupplierService.class).listActive(), app.bean(ProductService.class).listActive()}, d -> {
            @SuppressWarnings("unchecked") List<Supplier> s = (List<Supplier>) d[0];
            @SuppressWarnings("unchecked") List<Product> p = (List<Product>) d[1];
            if (s.isEmpty() || p.isEmpty()) { Dlg.info("Cannot create an order", "You need at least one active supplier and one active product first."); return; }
            NewOrderDialog.show(s, p).ifPresent(r -> act(() -> app.bean(OrderService.class).createDraft(r.supplierId(), r.lines(), r.expected(), app.userId()),
                    o -> "Draft " + o.orderNumber + " created"));
        }));
        submit.setOnAction(e -> withSelected(table, o -> act(() -> app.bean(OrderService.class).submit(o.id, app.userId()), "Order submitted")));
        confirm.setOnAction(e -> withSelected(table, o -> act(() -> app.bean(OrderService.class).confirm(o.id, app.userId()), "Order confirmed")));
        cancel.setOnAction(e -> withSelected(table, o -> {
            if (Dlg.confirm("Cancel order", "Cancel " + o.orderNumber + "? This cannot be undone.", "Cancel order", true))
                act(() -> app.bean(OrderService.class).cancel(o.id, app.userId()), "Order cancelled"); }));
        receive.setOnAction(e -> withSelected(table, o -> ReceiveDialog.show(o).ifPresent(m ->
                act(() -> app.bean(OrderService.class).receive(o.id, m, app.userId()), r -> "Order is now " + r.status))));
    }

    private void bind(Button b, ObservableValue<PurchaseOrder> sel, OrderStatus... allowed) {
        List<OrderStatus> ok = List.of(allowed);
        b.disableProperty().bind(app.readOnly().or(Bindings.createBooleanBinding(() -> sel.getValue() == null || !ok.contains(sel.getValue().status), sel)));
    }

    @Override public void refresh() {
        String f = filter.getValue();
        OrderStatus st = f == null || "ALL".equals(f) ? null : OrderStatus.valueOf(f);
        Long keep = selected(table).map(o -> o.id).orElse(null);
        load(() -> app.bean(OrderService.class).list(st), l -> {
            table.setItems(FXCollections.observableArrayList(l));
            if (keep != null) l.stream().filter(o -> keep.equals(o.id)).findFirst().ifPresent(o -> table.getSelectionModel().select(o));
        });
    }
}
