package com.nexuswms.ui.components;

import com.nexuswms.model.*;
import com.nexuswms.ui.util.Icons;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;

/** Per-line receiving dialog supporting partial receipts. */
public final class ReceiveDialog {
    private ReceiveDialog() {}
    public static Optional<Map<Long, Integer>> show(PurchaseOrder o) {
        Dialog<Map<Long, Integer>> d = new Dialog<>();
        d.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        GridPane g = new GridPane(); g.getStyleClass().add("nx-form");
        String[] heads = {"Product", "Ordered", "Received", "Receive now"};
        for (int c = 0; c < heads.length; c++) { Label h = new Label(heads[c].toUpperCase()); h.getStyleClass().add("nx-section-label"); g.add(h, c, 0); }
        Map<Long, Spinner<Integer>> spinners = new LinkedHashMap<>();
        int row = 1;
        for (OrderItem it : o.items) {
            int rem = Math.max(0, it.remaining());
            Spinner<Integer> sp = ProductDialog.spinner(0, Math.max(1, rem), rem); sp.setDisable(rem == 0); sp.setPrefWidth(120);
            spinners.put(it.id, sp);
            Label name = new Label(it.product.sku + " – " + it.product.name); name.setMinWidth(220);
            g.add(name, 0, row); g.add(new Label("" + it.quantityOrdered), 1, row); g.add(new Label("" + it.quantityReceived), 2, row); g.add(sp, 3, row++);
        }
        d.getDialogPane().setContent(g);
        DialogStyler.style(d, "Receive " + o.orderNumber, "Partial receipts are fine – the order stays open until every line is complete.", Icons.TRUCK_LOADING);
        d.setResultConverter(b -> {
            if (b != ButtonType.OK) return null;
            Map<Long, Integer> m = new HashMap<>();
            spinners.forEach((k, v) -> { ProductDialog.commit(v); m.put(k, v.getValue() == null ? 0 : v.getValue()); });
            return m; });
        return d.showAndWait();
    }
}
