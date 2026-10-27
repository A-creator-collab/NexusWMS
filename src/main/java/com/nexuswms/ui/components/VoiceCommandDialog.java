package com.nexuswms.ui.components;

import com.nexuswms.ui.AppContext;
import com.nexuswms.ui.util.FxTaskHelper;
import com.nexuswms.ui.util.Icons;
import com.nexuswms.voice.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import org.slf4j.*;
import java.util.function.Consumer;

/**
 * Voice / command palette. The microphone records one utterance (Vosk, offline), the transcript lands in the text box,
 * the parser shows how it was understood, and nothing runs until the user presses Run (Enter). Typing works the same way.
 */
public final class VoiceCommandDialog {
    private static final Logger log = LoggerFactory.getLogger(VoiceCommandDialog.class);
    private static boolean open;       // one voice window at a time
    private VoiceCommandDialog() {}

    public static void show(AppContext app, boolean autoListen, Consumer<String> navigate, Consumer<Boolean> listeningHook) {
        if (!app.online()) { Dlg.info("Voice command", "The database is offline, so commands are unavailable."); return; }
        if (open) return;
        SpeechToTextAdapter stt = app.bean(SpeechToTextAdapter.class);
        CommandParser parser = app.bean(CommandParser.class);
        VoiceService voice = app.bean(VoiceService.class);

        Dialog<Void> d = new Dialog<>();
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        d.initModality(Modality.NONE);

        Label status = new Label(stt.available() ? "Click the microphone and speak, or type a command." : stt.status());
        status.setWrapText(true); status.getStyleClass().add("muted"); status.setMaxWidth(440);
        Button mic = new Button(null, Icons.of(Icons.MIC, 22)); mic.getStyleClass().add("icon-button"); mic.setPrefSize(64, 64);
        mic.setDisable(!stt.available());
        TextField transcript = new TextField(); transcript.setPromptText("e.g. “show low stock for category packaging”");
        Label understood = new Label("Waiting for a command…"); understood.getStyleClass().add("muted"); understood.setWrapText(true); understood.setMaxWidth(440);
        TextArea result = new TextArea(); result.setEditable(false); result.setWrapText(true); result.setPrefRowCount(6); result.setVisible(false); result.setManaged(false);
        Button run = new Button("Run command", Icons.of(Icons.PLAY, 12)); run.getStyleClass().add("primary"); run.setDisable(true);
        Label hint = new Label("Try: “create order for five of SKU one zero zero one from Acme”, “open approvals”, “what is inventory value”.");
        hint.getStyleClass().add("muted"); hint.setWrapText(true); hint.setMaxWidth(440);

        VBox micBox = new VBox(mic); micBox.setAlignment(Pos.CENTER);
        VBox body = new VBox(12, micBox, status, transcript, understood, run, result, hint);
        body.setPadding(new Insets(4, 0, 8, 0)); body.setPrefWidth(460);
        d.getDialogPane().setContent(body);
        DialogStyler.style(d, "Voice command", "Speak or type – nothing runs until you confirm.", Icons.MIC);

        boolean[] listening = {false};
        transcript.textProperty().addListener((o, a, text) -> {
            ParsedCommand c = parser.parse(text);
            boolean ok = c.type() != ParsedCommand.Type.UNKNOWN;
            understood.setText(text == null || text.isBlank() ? "Waiting for a command…" : ok ? "Understood: " + describe(c) : "Not recognised yet – try “help”.");
            run.setDisable(text == null || text.isBlank());
        });

        Runnable stop = () -> stt.cancel();
        Runnable listen = () -> {
            if (listening[0]) { stop.run(); return; }
            listening[0] = true; listeningHook.accept(true); mic.getStyleClass().add("listening");
            status.setText("Listening… speak now"); transcript.clear(); result.setVisible(false); result.setManaged(false);
            FxTaskHelper.run(() -> stt.listen(p -> Platform.runLater(() -> transcript.setText(p))), text -> {
                listening[0] = false; listeningHook.accept(false); mic.getStyleClass().remove("listening");
                if (text.isPresent()) { transcript.setText(text.get()); status.setText("Heard you. Review, then press Run."); run.requestFocus(); }
                else status.setText("I didn't catch that. Click the microphone and try again.");
            }, err -> {
                listening[0] = false; listeningHook.accept(false); mic.getStyleClass().remove("listening");
                log.warn("Voice capture failed", err);
                status.setText(err instanceof SpeechException ? err.getMessage() : "Voice capture failed. Details are in the log.");
            });
        };
        mic.setOnAction(e -> listen.run());

        Runnable execute = () -> {
            String text = transcript.getText();
            if (text == null || text.isBlank()) return;
            ParsedCommand c = parser.parse(text);
            if (c.type() == ParsedCommand.Type.CREATE_ORDER && !Dlg.confirm("Create draft order", "Create a draft purchase order?\n" + describe(c), "Create draft", false)) return;
            run.setDisable(true);
            FxTaskHelper.run(() -> voice.handle(text, app.userId(), app.session().canMutate()), out -> {
                run.setDisable(false);
                result.setText(out); result.setVisible(true); result.setManaged(true);
                if (c.type() == ParsedCommand.Type.NAVIGATE) { navigate.accept(c.arg("view")); d.close(); }
            }, err -> { run.setDisable(false); FxTaskHelper.error(err); });
        };
        run.setOnAction(e -> execute.run());
        transcript.setOnAction(e -> execute.run());

        open = true;
        d.setOnHidden(e -> { open = false; stop.run(); listeningHook.accept(false); });
        d.setOnShown(e -> { if (autoListen && stt.available()) listen.run(); else transcript.requestFocus(); });
        d.show();
    }

    static String describe(ParsedCommand c) {
        return switch (c.type()) {
            case LOW_STOCK -> "show low stock" + (c.arg("category") == null ? "" : " in category “" + c.arg("category") + "”");
            case CREATE_ORDER -> "create draft order — " + c.arg("quantity") + " × " + c.arg("sku") + (c.arg("supplier") == null ? " (default supplier)" : " from “" + c.arg("supplier") + "”");
            case INVENTORY_VALUE -> "total inventory value";
            case FORECAST -> "reorder recommendations";
            case NAVIGATE -> "open " + c.arg("view");
            case HELP -> "help";
            default -> "unknown";
        };
    }
}
