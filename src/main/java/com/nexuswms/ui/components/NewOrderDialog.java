package com.nexuswms.ui.components;

import com.nexuswms.model.*;
import com.nexuswms.service.OrderService;
import com.nexuswms.ui.util.Icons;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public final class NewOrderDialog {
    public record Result(Long supplierId, LocalDate expected, List<OrderService.Line> lines) {}
    private NewOrderDialog() {}

    public static Optional<Result> show(List<Supplier> suppliers, List<Product> products) {
        Dialog<Result> d = new Dialog<>();
        d.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        ChoiceBox<Supplier> sup = new ChoiceBox<>(); sup.getItems().addAll(suppliers == null ? List.of() : suppliers);
        ChoiceBox<Product> prod = new ChoiceBox<>(); prod.getItems().addAll(products == null ? List.of() : products);
        prod.setConverter(new StringConverter<>() {
            @Override public String toString(Product p) { return p == null ? "" : p.sku + " – " + p.name; }
            @Override public Product fromString(String s) { return null; } });
        DatePicker expected = new DatePicker(); expected.setPromptText("Default: today + lead time");
        expected.setDayCellFactory(dp -> new DateCell() { @Override public void updateItem(LocalDate item, boolean empty) {
            super.updateItem(item, empty); setDisable(empty || (item != null && item.isBefore(LocalDate.now()))); } });
        Spinner<Integer> qty = ProductDialog.spinner(1, OrderService.MAX_QTY, 1);
        TextField cost = new TextField(); cost.setPromptText("Default: product price");
        List<OrderService.Line> lines = new ArrayList<>();
        ListView<String> view = new ListView<>(); view.setPrefHeight(120);
        Label empty = new Label("No lines yet"); empty.getStyleClass().add("muted"); view.setPlaceholder(empty);
        Button add = new Button("Add line", Icons.of(Icons.PLUS, 12)); add.getStyleClass().add("small");
        Button remove = new Button("Remove selected"); remove.getStyleClass().addAll("small", "ghost");
        add.setOnAction(e -> {
            ProductDialog.commit(qty); FormSupport.clear(prod); FormSupport.clear(cost);
            Product p = prod.getValue();
            if (p == null) { FormSupport.decorate(prod, "Choose a product"); return; }
            BigDecimal c = null;
            if (!cost.getText().isBlank()) {
                try { c = new BigDecimal(cost.getText().trim()); if (c.signum() < 0) throw new NumberFormatException(); }
                catch (RuntimeException ex) { FormSupport.decorate(cost, "Enter a non-negative number"); return; }
            }
            lines.add(new OrderService.Line(p.id, qty.getValue(), c));
            view.getItems().add(qty.getValue() + " × " + p.sku + " – " + p.name + (c == null ? "" : "  @ " + c.toPlainString()));
        });
        remove.setOnAction(e -> { int i = view.getSelectionModel().getSelectedIndex(); if (i >= 0) { view.getItems().remove(i); lines.remove(i); } });
        GridPane g = FormSupport.grid();
        int r = FormSupport.row(g, 0, "Supplier", sup); r = FormSupport.row(g, r, "Expected date", expected);
        r = FormSupport.section(g, r, "Add a line");
        r = FormSupport.row(g, r, "Product", prod); r = FormSupport.row(g, r, "Quantity", qty); r = FormSupport.row(g, r, "Unit cost ($)", cost);
        g.add(new HBox(8, add, remove), 1, r++);
        g.add(view, 0, r, 2, 1);
        d.getDialogPane().setContent(g);
        DialogStyler.style(d, "New purchase order", "Creates a draft you can review, then submit.", Icons.ORDERS);
        Button ok = (Button) d.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            FormSupport.clear(sup); FormSupport.clear(view);
            if (sup.getValue() == null) { ev.consume(); FormSupport.decorate(sup, "Choose a supplier"); }
            if (lines.isEmpty()) { ev.consume(); FormSupport.decorate(view, "Add at least one line"); }
        });
        d.setResultConverter(b -> b == ButtonType.OK && sup.getValue() != null ? new Result(sup.getValue().id, expected.getValue(), List.copyOf(lines)) : null);
        return d.showAndWait();
    }
}
