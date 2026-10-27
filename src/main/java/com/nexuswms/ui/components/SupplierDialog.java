package com.nexuswms.ui.components;

import com.nexuswms.service.SupplierForm;
import com.nexuswms.ui.util.Icons;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import java.util.*;

public final class SupplierDialog {
    private SupplierDialog() {}
    public static Optional<SupplierForm> show(SupplierForm existing) {
        Dialog<SupplierForm> d = new Dialog<>();
        d.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        TextField name = new TextField(), email = new TextField(), phone = new TextField();
        email.setPromptText("orders@supplier.com"); phone.setPromptText("+1 555 0100");
        Spinner<Integer> lead = ProductDialog.spinner(0, 365, 7);
        if (existing != null) { name.setText(existing.name); email.setText(existing.contactEmail); phone.setText(existing.phone); lead.getValueFactory().setValue(existing.leadTimeDays); }
        GridPane g = FormSupport.grid();
        int r = FormSupport.row(g, 0, "Name", name); r = FormSupport.row(g, r, "Email", email);
        r = FormSupport.row(g, r, "Phone", phone); FormSupport.row(g, r, "Lead time (days)", lead);
        d.getDialogPane().setContent(g);
        DialogStyler.style(d, existing == null ? "Add supplier" : "Edit supplier", "Lead time drives reorder quantities and safety stock.", Icons.SUPPLIERS);
        java.util.function.Supplier<SupplierForm> read = () -> {
            ProductDialog.commit(lead);
            SupplierForm f = new SupplierForm();
            f.name = name.getText(); f.contactEmail = email.getText().isBlank() ? null : email.getText().trim();
            f.phone = phone.getText(); f.leadTimeDays = lead.getValue() == null ? 0 : lead.getValue(); return f; };
        Map<String, Node> fields = Map.of("name", name, "contactEmail", email, "phone", phone, "leadTimeDays", lead);
        FormSupport.validateOnOk(d, read, fields);
        d.setOnShown(e -> name.requestFocus());
        d.setResultConverter(b -> b == ButtonType.OK ? read.get() : null);
        return d.showAndWait();
    }
}
