package com.nexuswms;

import com.nexuswms.security.AuthService;
import com.nexuswms.service.BusinessException;
import com.nexuswms.service.PasswordPolicy;
import com.nexuswms.ui.*;
import com.nexuswms.ui.components.*;
import com.nexuswms.ui.util.*;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.slf4j.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** JavaFX entry point. Starts the Spring context in init(), builds the UI in start(). Launch via NexusLauncher. */
public class NexusWmsApplication extends javafx.application.Application {
    private static final Logger log = LoggerFactory.getLogger(NexusWmsApplication.class);
    private volatile ConfigurableApplicationContext ctx;
    private volatile String offlineReason;
    private Stage stage;
    private MainWindow window;

    @Override public void init() { connect(); }

    private synchronized void connect() {
        try {
            if (ctx != null) { try { ctx.close(); } catch (RuntimeException ignored) { } ctx = null; }
            ctx = new SpringApplicationBuilder(NexusBoot.class).headless(false).run();
            offlineReason = null;
        } catch (Throwable t) {
            log.error("Could not start the backend", t);
            ctx = null; offlineReason = describe(t);
        }
    }

    @Override
    public void start(Stage primary) {
        stage = primary;
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> { log.error("Uncaught exception on {}", t.getName(), e); FxTaskHelper.error(e); });
        ThemeManager.apply();
        DialogStyler.setOwner(stage);
        stage.setTitle("NexusWMS"); stage.setMinWidth(1100); stage.setMinHeight(700); stage.setWidth(1440); stage.setHeight(880);
        buildUi();
    }

    private void buildUi() {
        AppContext app = new AppContext(ctx, offlineReason);
        if (app.online()) {
            String[] typed = new String[1];
            if (!login(app, typed)) { Platform.exit(); return; }
            if (PasswordPolicy.isKnownDefault(typed[0]) && !PasswordDialog.changeOwn(app, false)) { Platform.exit(); return; }   // forced, not skippable
        }
        if (window != null) window.shutdown();
        window = new MainWindow(app, this::reconnect);
        Scene scene = new Scene(window.root());
        scene.getStylesheets().add(DialogStyler.css());
        ThemeManager.decorate(window.root());
        window.installShortcuts(scene);
        stage.setScene(scene); stage.show();
        if (!app.online()) Dlg.error("Database unavailable", app.offlineReason == null ? "The database could not be reached." : app.offlineReason);
    }

    private void reconnect() { FxTaskHelper.run(() -> { connect(); return ctx != null; }, ok -> buildUi()); }

    /** Styled sign-in dialog. Wrong credentials keep the dialog open and show the reason inline. */
    private boolean login(AppContext app, String[] typedPassword) {
        Dialog<Boolean> d = new Dialog<>();
        d.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, new ButtonType("Sign in", ButtonBar.ButtonData.OK_DONE));
        ButtonType signIn = d.getDialogPane().getButtonTypes().get(1);
        TextField user = new TextField(); user.setPromptText("Username");
        PasswordField pass = new PasswordField(); pass.setPromptText("Password");
        Label err = new Label(); err.getStyleClass().add("login-error"); err.setWrapText(true); err.setManaged(false); err.setVisible(false);
        var grid = FormSupport.grid();
        int r = FormSupport.row(grid, 0, "Username", user); FormSupport.row(grid, r, "Password", pass);
        VBox body = new VBox(8, grid, err);
        d.getDialogPane().setContent(body);
        DialogStyler.style(d, "Sign in to NexusWMS", "Warehouse operations console", com.nexuswms.ui.util.Icons.WAREHOUSE);
        Button ok = (Button) d.getDialogPane().lookupButton(signIn);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            try { app.bean(AuthService.class).login(user.getText(), pass.getText()); typedPassword[0] = pass.getText(); }
            catch (BusinessException ex) { show(err, ex.getMessage()); ev.consume(); pass.clear(); pass.requestFocus(); }
            catch (RuntimeException ex) { log.error("Login failed", ex); show(err, "Sign-in failed. Details are in the log."); ev.consume(); }
        });
        d.setOnShown(e -> user.requestFocus());
        d.setResultConverter(b -> b == signIn);
        return d.showAndWait().orElse(false);
    }

    private static void show(Label l, String msg) { l.setText(msg); l.setManaged(true); l.setVisible(true); }

    @Override public void stop() { if (window != null) window.shutdown(); if (ctx != null) ctx.close(); }

    static String describe(Throwable t) {
        Throwable root = t; while (root.getCause() != null) root = root.getCause();
        String url = System.getenv().getOrDefault("NEXUS_DB_URL", "jdbc:mysql://localhost:3306/nexuswms").split("\\?")[0];
        String user = System.getenv().getOrDefault("NEXUS_DB_USER", "nexus");
        return "Could not start the database layer.\n\nTarget: " + url + "\nUser: " + user + "\nCause: " + root.getClass().getSimpleName() + ": " + root.getMessage()
                + "\n\nCheck that MySQL is running, the database exists, and NEXUS_DB_URL / NEXUS_DB_USER / NEXUS_DB_PASSWORD are correct.";
    }
}
