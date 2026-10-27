package com.nexuswms.ui.components;

import com.nexuswms.service.BusinessException;
import com.nexuswms.service.PasswordPolicy;
import com.nexuswms.service.UserService;
import com.nexuswms.ui.AppContext;
import com.nexuswms.ui.util.Icons;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.slf4j.*;
import java.util.function.BiFunction;

/** Password dialogs. Server-side rejections (policy, wrong current password) are shown inline; the dialog stays open until it succeeds. */
public final class PasswordDialog {
    private static final Logger log = LoggerFactory.getLogger(PasswordDialog.class);
    private PasswordDialog() {}

    /** Change the signed-in user's own password. When {@code cancellable} is false the user must complete it (e.g. seeded default password). */
    public static boolean changeOwn(AppContext app, boolean cancellable) {
        UserService svc = app.bean(UserService.class);
        Long me = app.userId();
        return show(cancellable ? "Change password" : "Choose a new password",
                cancellable ? "Pick a strong password you do not use elsewhere." : "You are using a default password. You must choose a new one before continuing.",
                true, cancellable, (current, fresh) -> svc.changeOwnPasswordSafe(me, current, fresh));
    }

    /** Admin sets a new password for another user. */
    public static boolean reset(AppContext app, Long userId, String username) {
        UserService svc = app.bean(UserService.class);
        Long actor = app.userId();
        return show("Reset password", "New password for " + username, false, true, (current, fresh) -> svc.resetPasswordSafe(userId, fresh, actor));
    }

    /** submit returns null on success or an error message to display. */
    private static boolean show(String title, String subtitle, boolean askCurrent, boolean cancellable, BiFunction<String, String, String> submit) {
        Dialog<Boolean> d = new Dialog<>();
        ButtonType ok = new ButtonType("Save password", ButtonBar.ButtonData.OK_DONE);
        if (cancellable) d.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        d.getDialogPane().getButtonTypes().add(ok);
        PasswordField current = new PasswordField(), fresh = new PasswordField(), confirm = new PasswordField();
        Label hint = new Label("At least " + PasswordPolicy.MIN_LENGTH + " characters with a letter and a digit."); hint.getStyleClass().add("muted");
        Label err = new Label(); err.getStyleClass().add("login-error"); err.setWrapText(true); err.setMaxWidth(380); err.setManaged(false); err.setVisible(false);
        GridPane g = FormSupport.grid();
        int r = 0;
        if (askCurrent) r = FormSupport.row(g, r, "Current password", current);
        r = FormSupport.row(g, r, "New password", fresh); FormSupport.row(g, r, "Confirm", confirm);
        VBox body = new VBox(6, g, hint, err);
        d.getDialogPane().setContent(body);
        DialogStyler.style(d, title, subtitle, Icons.USER);
        Button okBtn = (Button) d.getDialogPane().lookupButton(ok);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            FormSupport.clear(confirm);
            if (!fresh.getText().equals(confirm.getText())) { ev.consume(); FormSupport.decorate(confirm, "Passwords do not match"); return; }
            String message;
            try { message = submit.apply(current.getText(), fresh.getText()); }
            catch (RuntimeException ex) { log.error("Password change failed", ex); message = "Could not save the password. Details are in the log."; }
            if (message != null) { ev.consume(); err.setText(message); err.setManaged(true); err.setVisible(true); }
        });
        d.setOnShown(e -> (askCurrent ? current : fresh).requestFocus());
        d.setResultConverter(b -> b == ok);
        return d.showAndWait().orElse(false);
    }
}
