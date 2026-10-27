package com.nexuswms.ui.components;

import com.nexuswms.ui.util.Icons;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.util.Optional;

/** Styled modal helpers (all go through {@link DialogStyler}; no default OS-looking dialogs anywhere). */
public final class Dlg {
    private Dlg() {}

    public static boolean confirm(String text) { return confirm("Please confirm", text, "Confirm", false); }

    public static boolean confirm(String title, String text, String confirmLabel, boolean danger) {
        Dialog<Boolean> d = new Dialog<>();
        ButtonType ok = new ButtonType(confirmLabel, ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ok);
        d.getDialogPane().setContent(body(text));
        DialogStyler.style(d, title, null, danger ? Icons.WARNING : Icons.INFO);
        if (danger) DialogStyler.danger(d, ok);
        d.setResultConverter(b -> b == ok);
        return d.showAndWait().orElse(false);
    }

    public static void info(String title, String text) { notice(title, text, Icons.INFO); }
    public static void error(String title, String text) { notice(title, text, Icons.WARNING); }

    private static void notice(String title, String text, String icon) {
        Dialog<Void> d = new Dialog<>();
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        d.getDialogPane().setContent(body(text));
        DialogStyler.style(d, title, null, icon);
        ((Button) d.getDialogPane().lookupButton(ButtonType.CLOSE)).getStyleClass().remove("ghost");
        ((Button) d.getDialogPane().lookupButton(ButtonType.CLOSE)).getStyleClass().add("primary");
        d.showAndWait();
    }

    public static void text(String title, String text) {
        TextArea ta = new TextArea(text == null ? "" : text);
        ta.setEditable(false); ta.setWrapText(true); ta.setPrefRowCount(12); ta.setPrefColumnCount(56);
        Dialog<Void> d = new Dialog<>();
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        d.getDialogPane().setContent(pad(ta));
        DialogStyler.style(d, title, null, Icons.FILE);
        d.showAndWait();
    }

    public static Optional<String> prompt(String title, String header, String initial) {
        TextField tf = new TextField(initial == null ? "" : initial); tf.setPrefColumnCount(32);
        Dialog<String> d = new Dialog<>();
        d.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        d.getDialogPane().setContent(pad(tf));
        DialogStyler.style(d, title, header, Icons.EDIT);
        d.setOnShown(e -> tf.requestFocus());
        d.setResultConverter(b -> b == ButtonType.OK ? tf.getText() : null);
        return d.showAndWait();
    }

    private static VBox body(String text) {
        Label l = new Label(text == null ? "" : text); l.setWrapText(true); l.setMaxWidth(430);
        return pad(l);
    }
    private static VBox pad(javafx.scene.Node n) { VBox b = new VBox(n); b.setPadding(new Insets(4, 0, 8, 0)); return b; }
}
