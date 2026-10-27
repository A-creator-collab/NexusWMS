package com.nexuswms.ui;

import com.nexuswms.agents.AssistantService;
import com.nexuswms.service.SettingsService;
import com.nexuswms.ui.components.PasswordDialog;
import com.nexuswms.ui.util.Icons;
import com.nexuswms.ui.util.ThemeManager;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class SettingsView extends BaseView {
    private final CheckBox rep = new CheckBox("Replenishment agent"), risk = new CheckBox("Risk agent"), order = new CheckBox("Order agent");
    private final Spinner<Double> riskT = new Spinner<>(0.05, 0.95, 0.30, 0.05), poT = new Spinner<>(0.0, 1_000_000.0, 1000.0, 100.0);
    private final ChoiceBox<String> policy = new ChoiceBox<>();
    private final Button save = new Button("Save settings");

    public SettingsView(AppContext app) {
        super(app);
        setPageHeader("Settings", "Agents, thresholds and approval policy");
        riskT.setEditable(true); poT.setEditable(true);
        policy.getItems().addAll(SettingsService.POLICY_ALL, SettingsService.POLICY_OVER, SettingsService.POLICY_NONFIN);
        Label note = new Label("v1 safety rule: agents never auto-execute. Financial and destructive actions always need approval; the policy below "
                + "is stored for future automation of low-risk, non-financial actions.");
        note.setWrapText(true); note.getStyleClass().add("muted");
        GridPane g = new GridPane(); g.setHgap(12); g.setVgap(10); g.setPadding(new Insets(4));
        g.add(new Label("Agents"), 0, 0); g.add(new VBox(6, rep, risk, order), 1, 0);
        g.addRow(1, new Label("Stock-out probability threshold"), riskT);
        g.addRow(2, new Label("Approval threshold ($)"), poT);
        g.addRow(3, new Label("Approval policy"), policy);
        g.add(note, 0, 4, 2, 1);
        g.add(save, 1, 5);
        String ai = app.online() ? app.bean(AssistantService.class).modeLabel() : "Offline";
        String voiceStatus = app.online() ? app.bean(com.nexuswms.voice.SpeechToTextAdapter.class).status() : "Offline";
        Label info = new Label("Assistant: " + ai + "\nVoice: " + voiceStatus + "\nTheme: " + (ThemeManager.isDark() ? "dark" : "light") + " (toggle in the top bar)");
        info.getStyleClass().add("muted");
        VBox cardBox = card(g); cardBox.setMaxWidth(720);
        Button changePw = button("Change my password", Icons.USER, null);
        changePw.disableProperty().bind(app.dbUp.not());
        changePw.setOnAction(e -> PasswordDialog.changeOwn(app, true));
        VBox account = card(sectionTitle("Account"), changePw); account.setMaxWidth(720);
        setCenter(new VBox(14, cardBox, account, info));
        save.disableProperty().bind(app.cannotApprove());
        save.setOnAction(e -> act(() -> { app.bean(SettingsService.class).update(rep.isSelected(), risk.isSelected(), order.isSelected(),
                riskT.getValue() == null ? 0.3 : riskT.getValue(), poT.getValue() == null ? 0 : poT.getValue(), policy.getValue() == null ? SettingsService.POLICY_ALL : policy.getValue(), app.userId()); return null; }, "Settings saved"));
    }

    @Override public void refresh() {
        if (!app.online()) return;
        SettingsService s = app.bean(SettingsService.class);
        rep.setSelected(s.agentEnabled("replenishment")); risk.setSelected(s.agentEnabled("risk")); order.setSelected(s.agentEnabled("order"));
        riskT.getValueFactory().setValue(s.riskThreshold()); poT.getValueFactory().setValue(s.poThreshold()); policy.setValue(s.approvalPolicy());
    }
}
