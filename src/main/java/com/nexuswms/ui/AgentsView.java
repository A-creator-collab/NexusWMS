package com.nexuswms.ui;

import com.nexuswms.agents.*;
import com.nexuswms.ui.components.*;
import com.nexuswms.ui.util.*;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class AgentsView extends BaseView {
    private final VBox messages = new VBox(10);
    private final ScrollPane scroll = new ScrollPane(messages);
    private final TextField input = new TextField();
    private final TextArea report = new TextArea();
    private final Button send = button("Send", Icons.SEND, "primary"), runAgents = button("Run agents now", Icons.PLAY, null), clear = button("New chat", Icons.REFRESH, "ghost");
    private final Label mode = new Label();
    private boolean waiting;

    public AgentsView(AppContext app) {
        super(app);
        setPageHeader("Agents & Assistant", "Ask questions in plain language; agents propose actions for your approval", runAgents);
        Label disclaimer = new Label("AI proposes, you approve. The assistant is read-only; agent proposals appear under Approvals.");
        disclaimer.setWrapText(true); HBox dis = new HBox(disclaimer); dis.getStyleClass().add("disclaimer");
        mode.getStyleClass().add("muted");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        HBox modeRow = new HBox(8, Icons.of(app.online() ? Icons.AGENTS : Icons.DATABASE, 13), mode, sp, clear); modeRow.setAlignment(Pos.CENTER_LEFT);

        messages.setPadding(new Insets(6)); scroll.setFitToWidth(true);
        messages.heightProperty().addListener((o, a, b) -> scroll.setVvalue(1.0));
        input.setPromptText("Ask about stock, orders, forecasts…"); HBox.setHgrow(input, Priority.ALWAYS);
        HBox chips = new HBox(6);
        for (String q : new String[]{"Show low stock", "Reorder recommendations", "Inventory trends", "Summary"}) {
            Button c = button(q, null, "small"); c.setOnAction(e -> { input.setText(q); ask(); }); chips.getChildren().add(c);
        }
        HBox row = new HBox(8, input, send);
        VBox chat = card(dis, modeRow, scroll, chips, row); VBox.setVgrow(scroll, Priority.ALWAYS); HBox.setHgrow(chat, Priority.ALWAYS);

        report.setPromptText("Report draft – use “Add to report” on any answer"); report.setWrapText(true);
        VBox right = card(sectionTitle("Report draft"), report); right.setPrefWidth(340); right.setMinWidth(300); VBox.setVgrow(report, Priority.ALWAYS);
        HBox body = new HBox(16, chat, right); setCenter(body);

        send.disableProperty().bind(app.dbUp.not());
        runAgents.disableProperty().bind(app.readOnly());
        send.setOnAction(e -> ask()); input.setOnAction(e -> ask());
        clear.setOnAction(e -> { if (app.online()) app.bean(AssistantService.class).clearHistory(); messages.getChildren().clear(); greet(); });
        runAgents.setOnAction(e -> act(() -> app.bean(AgentOrchestrator.class).runAll(), n -> n + " new proposal(s) – see Approvals"));
        greet();
    }

    private void greet() { bubble("Hi! I can answer questions about stock, orders, forecasts and approvals. Try “show low stock” or “create order for 5 of SKU-1001”.", false); }

    private void ask() {
        if (!app.online() || waiting) return;
        String q = input.getText() == null ? "" : input.getText().trim();
        if (q.isEmpty()) return;
        input.clear(); bubble(q, true);
        Label answer = bubble("Thinking…", false);
        StringBuilder acc = new StringBuilder();
        waiting = true; send.setText("…");
        FxTaskHelper.run(() -> app.bean(AssistantService.class).ask(q, app.userId(), app.session().canMutate(), chunk -> Platform.runLater(() -> {
            acc.append(chunk); answer.setText(acc.toString()); })), full -> { waiting = false; send.setText("Send"); addInsert(answer, full); },
            err -> { waiting = false; send.setText("Send"); answer.setText("Sorry – something went wrong. Details are in the log."); FxTaskHelper.error(err); });
    }

    private void addInsert(Label answer, String full) {
        if (full == null || full.isBlank() || !(answer.getParent() instanceof VBox box)) return;
        Button b = button("Add to report", Icons.PLUS, "small"); b.getStyleClass().add("ghost");
        b.setOnAction(e -> { report.appendText("• " + full.replace("\n", "\n  ") + "\n\n"); b.setDisable(true); });
        box.getChildren().add(b);
    }

    private Label bubble(String text, boolean mine) {
        Label l = new Label(text); l.setWrapText(true); l.setMaxWidth(560);
        l.getStyleClass().addAll("bubble", mine ? "bubble-user" : "bubble-assistant");
        VBox box = new VBox(4, l); box.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        HBox r = new HBox(box); r.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT); messages.getChildren().add(r);
        return l;
    }

    @Override public void refresh() { mode.setText(app.online() ? app.bean(AssistantService.class).modeLabel() : "Offline"); }
}
