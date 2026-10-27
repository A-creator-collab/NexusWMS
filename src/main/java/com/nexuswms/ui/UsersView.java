package com.nexuswms.ui;

import com.nexuswms.model.Role;
import com.nexuswms.service.UserService;
import com.nexuswms.service.UserService.UserRow;
import com.nexuswms.ui.components.*;
import com.nexuswms.ui.util.*;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Administrator-only user management. Every action is re-checked and audited in {@link UserService}. */
public class UsersView extends BaseView {
    private final TableView<UserRow> table = new TableView<>();
    private final Button add = button("Add user", Icons.PLUS, "primary"), role = button("Change role", Icons.SLIDERS, null),
            reset = button("Reset password", Icons.USER, null), toggle = button("Deactivate", Icons.BAN, null);

    public UsersView(AppContext app) {
        super(app);
        setPageHeader("Users", "Accounts, roles and access", add);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        HBox toolbar = new HBox(8, sp, role, reset, toggle); toolbar.getStyleClass().add("toolbar");
        table.getColumns().addAll(col("Username", (UserRow u) -> u.username(), 200), badgeCol("Role", u -> u.role() == null ? "" : u.role().name(), 120),
                badgeCol("Status", u -> u.active() ? "OK" : "CANCELLED", 100), col("Created", u -> Formatters.dateTime(u.createdAt()), 150));
        styleTable(table, Icons.USER, "No users", "Create the first account.");
        VBox body = new VBox(12, toolbar, flushCard(table)); VBox.setVgrow(body.getChildren().get(1), Priority.ALWAYS); setCenter(body);

        var none = table.getSelectionModel().selectedItemProperty().isNull();
        add.disableProperty().bind(app.dbUp.not());
        for (Button b : new Button[]{role, reset, toggle}) b.disableProperty().bind(app.dbUp.not().or(none));
        table.getSelectionModel().selectedItemProperty().addListener((o, a, u) -> toggle.setText(u != null && !u.active() ? "Activate" : "Deactivate"));

        add.setOnAction(e -> UserDialog.create().ifPresent(r -> act(() -> app.bean(UserService.class).create(r.username(), r.password(), r.role(), app.userId()),
                u -> "User " + u.username() + " created")));
        role.setOnAction(e -> withSelected(table, u -> UserDialog.chooseRole(u.username(), u.role()).ifPresent(r ->
                act(() -> { app.bean(UserService.class).setRole(u.id(), r, app.userId()); return null; }, "Role updated"))));
        reset.setOnAction(e -> withSelected(table, u -> { if (PasswordDialog.reset(app, u.id(), u.username())) { FxTaskHelper.info("Done", "Password reset for " + u.username()); refresh(); } }));
        toggle.setOnAction(e -> withSelected(table, u -> {
            boolean activate = !u.active();
            if (activate || Dlg.confirm("Deactivate user", u.username() + " will no longer be able to sign in.", "Deactivate", true))
                act(() -> { app.bean(UserService.class).setActive(u.id(), activate, app.userId()); return null; }, activate ? "User activated" : "User deactivated"); }));
    }

    @Override public void refresh() {
        Long keep = selected(table).map(UserRow::id).orElse(null);
        load(() -> app.bean(UserService.class).list(), l -> {
            table.setItems(FXCollections.observableArrayList(l));
            if (keep != null) l.stream().filter(u -> keep.equals(u.id())).findFirst().ifPresent(u -> table.getSelectionModel().select(u));
        });
    }
}
