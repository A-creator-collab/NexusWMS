package com.nexuswms.ui.components;

import com.nexuswms.model.Supplier;
import com.nexuswms.service.ProductForm;
import com.nexuswms.ui.util.Icons;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;
import java.math.BigDecimal;
import java.util.*;

public final class ProductDialog {
    private ProductDialog() {}

    public static Optional<ProductForm> show(List<Supplier> suppliers, ProductForm existing) {
        boolean edit = existing != null;
        Dialog<ProductForm> d = new Dialog<>();
        d.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        TextField sku = new TextField(), name = new TextField(), cat = new TextField(), price = new TextField("0.00"), bin = new TextField();
        sku.setPromptText("e.g. SKU-1001"); bin.setPromptText("e.g. A-01"); price.setPromptText("0.00");
        TextArea desc = new TextArea(); desc.setPrefRowCount(3); desc.setWrapText(true);
        Spinner<Integer> rp = spinner(0, 10_000_000, 0), ss = spinner(0, 10_000_000, 0), qty = spinner(0, 10_000_000, 0);
        ChoiceBox<Supplier> sup = new ChoiceBox<>();
        sup.getItems().add(null); sup.getItems().addAll(suppliers == null ? List.of() : suppliers);
        sup.setConverter(new StringConverter<>() {
            @Override public String toString(Supplier s) { return s == null ? "— none —" : s.name; }
            @Override public Supplier fromString(String s) { return null; }
        });
        sup.setValue(null);
        if (edit) {
            sku.setText(existing.sku); name.setText(existing.name); cat.setText(existing.category); desc.setText(existing.description);
            price.setText(existing.unitPrice == null ? "" : existing.unitPrice.toPlainString()); bin.setText(existing.bin);
            rp.getValueFactory().setValue(existing.reorderPoint); ss.getValueFactory().setValue(existing.safetyStock);
            qty.getValueFactory().setValue(existing.initialQuantity);
            if (suppliers != null) suppliers.stream().filter(s -> s.id != null && s.id.equals(existing.supplierId)).findFirst().ifPresent(sup::setValue);
            qty.setDisable(true);   // stock quantity is changed via "Adjust stock" so the ledger stays correct
        }
        GridPane g = FormSupport.grid();
        int r = FormSupport.section(g, 0, "Product");
        r = FormSupport.row(g, r, "SKU", sku); r = FormSupport.row(g, r, "Name", name); r = FormSupport.row(g, r, "Category", cat);
        r = FormSupport.row(g, r, "Description", desc);
        r = FormSupport.section(g, r, "Pricing & replenishment");
        r = FormSupport.row(g, r, "Unit price ($)", price); r = FormSupport.row(g, r, "Reorder point", rp);
        r = FormSupport.row(g, r, "Safety stock", ss); r = FormSupport.row(g, r, "Supplier", sup);
        r = FormSupport.section(g, r, "Stock & location");
        r = FormSupport.row(g, r, edit ? "On hand" : "Initial quantity", qty); FormSupport.row(g, r, "Bin location", bin);
        d.getDialogPane().setContent(g);
        DialogStyler.style(d, edit ? "Edit product" : "Add product", edit ? "Update catalogue details. Use “Adjust stock” to change quantities." : "Create a catalogue item with its opening stock.", Icons.BOX);
        d.getDialogPane().lookupButton(ButtonType.OK).setDisable(false);

        Map<String, Node> fields = Map.of("sku", sku, "name", name, "category", cat, "description", desc, "unitPrice", price, "reorderPoint", rp,
                "safetyStock", ss, "initialQuantity", qty, "bin", bin);
        FormSupport.validateOnOk(d, () -> read(sku, name, cat, desc, price, rp, ss, sup, qty, bin), fields);
        d.setOnShown(e -> sku.requestFocus());
        d.setResultConverter(b -> b == ButtonType.OK ? read(sku, name, cat, desc, price, rp, ss, sup, qty, bin) : null);
        return d.showAndWait();
    }

    static Spinner<Integer> spinner(int min, int max, int v) {
        Spinner<Integer> s = new Spinner<>(min, max, v); s.setEditable(true);
        s.getEditor().focusedProperty().addListener((o, a, focused) -> { if (!focused) commit(s); });   // never lose typed-but-uncommitted text
        return s;
    }

    static void commit(Spinner<Integer> s) {
        try { s.getValueFactory().setValue(Integer.valueOf(s.getEditor().getText().trim())); }
        catch (RuntimeException e) { s.getEditor().setText(String.valueOf(s.getValue())); }
    }

    private static ProductForm read(TextField sku, TextField name, TextField cat, TextArea desc, TextField price, Spinner<Integer> rp,
                                    Spinner<Integer> ss, ChoiceBox<Supplier> sup, Spinner<Integer> qty, TextField bin) {
        for (Spinner<Integer> s : List.of(rp, ss, qty)) commit(s);
        ProductForm f = new ProductForm();
        f.sku = sku.getText(); f.name = name.getText(); f.category = cat.getText(); f.description = desc.getText();
        try { f.unitPrice = new BigDecimal(price.getText().trim()); } catch (RuntimeException e) { f.unitPrice = null; }
        f.reorderPoint = rp.getValue() == null ? 0 : rp.getValue(); f.safetyStock = ss.getValue() == null ? 0 : ss.getValue();
        f.initialQuantity = qty.getValue() == null ? 0 : qty.getValue();
        f.supplierId = sup.getValue() == null ? null : sup.getValue().id; f.bin = bin.getText();
        return f;
    }
}
