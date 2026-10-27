package com.nexuswms.ui.components;

import jakarta.validation.*;
import javafx.event.ActionEvent;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.controlsfx.control.decoration.*;
import java.util.*;
import java.util.function.Supplier;

/** Bean Validation + ControlsFX field decoration for dialogs, plus consistent form layout. */
public final class FormSupport {
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private FormSupport() {}

    /** Two-column form grid (labels left, controls fill). */
    public static GridPane grid() {
        GridPane g = new GridPane(); g.getStyleClass().add("nx-form");
        ColumnConstraints a = new ColumnConstraints(130); a.setHalignment(HPos.LEFT);
        ColumnConstraints b = new ColumnConstraints(); b.setHgrow(Priority.ALWAYS); b.setFillWidth(true); b.setMinWidth(260);
        g.getColumnConstraints().addAll(a, b);
        return g;
    }

    public static int row(GridPane g, int row, String label, Node field) {
        Label l = new Label(label); l.getStyleClass().add("field-label");
        GridPane.setValignment(l, javafx.geometry.VPos.CENTER);
        g.add(l, 0, row); g.add(field, 1, row);
        if (field instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
        return row + 1;
    }

    public static int section(GridPane g, int row, String text) {
        Label l = new Label(text.toUpperCase()); l.getStyleClass().add("nx-section-label");
        g.add(l, 0, row, 2, 1); return row + 1;
    }

    public static void decorate(Node field, String message) {
        Label mark = new Label("!"); mark.getStyleClass().add("field-error"); Tooltip.install(mark, new Tooltip(message));
        Decorator.addDecoration(field, new GraphicDecoration(mark, Pos.CENTER_RIGHT, -6, 0));
    }
    public static void clear(Node field) { Decorator.removeAllDecorations(field); }

    /** Blocks the dialog's OK button while the bean has violations; marks each failing control. */
    public static <T> void validateOnOk(Dialog<?> dialog, Supplier<T> bean, Map<String, Node> fields) {
        Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        if (ok == null) return;
        ok.addEventFilter(ActionEvent.ACTION, ev -> {
            fields.values().forEach(FormSupport::clear);
            Set<ConstraintViolation<T>> v = VALIDATOR.validate(bean.get());
            if (v.isEmpty()) return;
            ev.consume();
            for (ConstraintViolation<T> c : v) {
                Node n = fields.get(c.getPropertyPath().toString());
                if (n != null) decorate(n, c.getMessage());
            }
        });
    }
}
