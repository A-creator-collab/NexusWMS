package com.nexuswms.ui.components;

import com.nexuswms.model.Role;
import com.nexuswms.ui.util.Icons;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.Optional;

public final class UserDialog {
    public record Result(String username, Role role, String password) {}
    private UserDialog() {}

    public static Optional<Result> create() {
        Dialog<Result> d = new Dialog<>();
        d.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        TextField name = new TextField(); name.setPromptText("e.g. j.smith");
        ChoiceBox<Role> role = new ChoiceBox<>(); role.getItems().addAll(Role.values()); role.setValue(Role.OPERATOR);
        PasswordField pw = new PasswordField(), confirm = new PasswordField();
        Label roleHelp = new Label(); roleHelp.getStyleClass().add("muted"); roleHelp.setWrapText(true); roleHelp.setMaxWidth(380);
        role.valueProperty().addListener((o, a, r) -> roleHelp.setText(help(r))); roleHelp.setText(help(Role.OPERATOR));
        GridPane g = FormSupport.grid();
        int r = FormSupport.row(g, 0, "Username", name); r = FormSupport.row(g, r, "Role", role);
        r = FormSupport.row(g, r, "Password", pw); FormSupport.row(g, r, "Confirm", confirm);
        d.getDialogPane().setContent(new VBox(6, g, roleHelp));
        DialogStyler.style(d, "Add user", "The password policy is enforced when you save.", Icons.USER);
        ((Button) d.getDialogPane().lookupButton(ButtonType.OK)).addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            for (var n : new javafx.scene.Node[]{name, role, confirm}) FormSupport.clear(n);
            if (name.getText() == null || name.getText().trim().length() < 3) { ev.consume(); FormSupport.decorate(name, "At least 3 characters"); }
            else if (role.getValue() == null) { ev.consume(); FormSupport.decorate(role, "Choose a role"); }
            else if (!pw.getText().equals(confirm.getText())) { ev.consume(); FormSupport.decorate(confirm, "Passwords do not match"); }
        });
        d.setOnShown(e -> name.requestFocus());
        d.setResultConverter(b -> b == ButtonType.OK ? new Result(name.getText().trim(), role.getValue(), pw.getText()) : null);
        return d.showAndWait();
    }

    public static Optional<Role> chooseRole(String username, Role current) {
        Dialog<Role> d = new Dialog<>();
        d.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        ChoiceBox<Role> role = new ChoiceBox<>(); role.getItems().addAll(Role.values()); role.setValue(current);
        Label help = new Label(help(current)); help.getStyleClass().add("muted"); help.setWrapText(true); help.setMaxWidth(380);
        role.valueProperty().addListener((o, a, r) -> help.setText(help(r)));
        GridPane g = FormSupport.grid(); FormSupport.row(g, 0, "Role", role);
        d.getDialogPane().setContent(new VBox(6, g, help));
        DialogStyler.style(d, "Change role", username, Icons.SLIDERS);
        d.setResultConverter(b -> b == ButtonType.OK ? role.getValue() : null);
        return d.showAndWait();
    }

    static String help(Role r) {
        if (r == null) return "";
        return switch (r) {
            case ADMIN -> "Everything, including user management.";
            case MANAGER -> "Everything except user management; can approve agent proposals.";
            case OPERATOR -> "Creates orders and adjusts stock; cannot approve.";
            case VIEWER -> "Read-only access.";
        };
    }
}
