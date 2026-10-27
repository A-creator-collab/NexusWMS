package com.nexuswms.ui.components;

import com.nexuswms.model.MovementReason;
import com.nexuswms.ui.util.Icons;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import java.util.Optional;

public final class AdjustDialog {
    public record Result(int delta, MovementReason reason, String note) {}
    private AdjustDialog() {}

    public static Optional<Result> show(String productLabel, int onHand) {
        Dialog<Result> d = new Dialog<>();
        d.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        Spinner<Integer> delta = ProductDialog.spinner(-1_000_000, 1_000_000, 0);
        ChoiceBox<MovementReason> reason = new ChoiceBox<>();
        reason.getItems().addAll(MovementReason.ADJUST, MovementReason.RECEIVE, MovementReason.SHIP, MovementReason.RETURN, MovementReason.SHRINK, MovementReason.TRANSFER);
        reason.setValue(MovementReason.ADJUST);
        TextField note = new TextField(); note.setPromptText("Optional – appears in the stock ledger");
        Label preview = new Label("New on-hand: " + onHand); preview.getStyleClass().add("muted");
        delta.valueProperty().addListener((o, a, v) -> preview.setText("New on-hand: " + (v == null ? onHand : onHand + v)));
        GridPane g = FormSupport.grid();
        int r = FormSupport.row(g, 0, "Change (+ / −)", delta);
        r = FormSupport.row(g, r, "Reason", reason); r = FormSupport.row(g, r, "Note", note);
        g.add(preview, 1, r);
        d.getDialogPane().setContent(g);
        DialogStyler.style(d, "Adjust stock", productLabel + " · on hand " + onHand, Icons.EXCHANGE);
        Button ok = (Button) d.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            ProductDialog.commit(delta);
            FormSupport.clear(delta); FormSupport.clear(reason);
            Integer v = delta.getValue(); MovementReason mr = reason.getValue();
            if (v == null || v == 0) { ev.consume(); FormSupport.decorate(delta, "Enter a non-zero change"); }
            else if (mr == null) { ev.consume(); FormSupport.decorate(reason, "Choose a reason"); }
            else if (onHand + v < 0) { ev.consume(); FormSupport.decorate(delta, "Stock cannot go below zero"); }
            else if ((mr == MovementReason.RECEIVE || mr == MovementReason.RETURN) && v < 0) { ev.consume(); FormSupport.decorate(delta, mr + " must be positive"); }
            else if ((mr == MovementReason.SHIP || mr == MovementReason.SHRINK) && v > 0) { ev.consume(); FormSupport.decorate(delta, mr + " must be negative"); }
        });
        d.setResultConverter(b -> b == ButtonType.OK ? new Result(delta.getValue(), reason.getValue(), note.getText()) : null);
        return d.showAndWait();
    }
}
