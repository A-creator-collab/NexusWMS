package com.nexuswms.ui;

import com.nexuswms.agents.*;
import com.nexuswms.agents.ApprovalService.ApprovalRow;
import com.nexuswms.ui.components.*;
import com.nexuswms.ui.util.*;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class ApprovalView extends BaseView {
    private final TableView<ApprovalRow> table = new TableView<>();
    private final TextArea rationale = new TextArea();
    private final CheckBox history = new CheckBox("Show decided");
    private final Button approve = button("Approve & execute", Icons.CHECK, "primary"), reject = button("Reject", Icons.BAN, null),
            changes = button("Request changes", Icons.COMMENT, null);

    public ApprovalView(AppContext app) {
        super(app);
        setPageHeader("Approvals", "Agents propose – you decide. Approving executes through the normal, audited services.");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        HBox toolbar = new HBox(8, history, sp, changes, reject, approve); toolbar.getStyleClass().add("toolbar");
        table.getColumns().addAll(col("Created", (ApprovalRow r) -> Formatters.dateTime(r.createdAt()), 135), col("Agent", ApprovalRow::agent, 140),
                col("Action", r -> r.action() == null ? "" : r.action().name(), 160), col("Target", ApprovalRow::target, 250), col("Qty", r -> "" + r.quantity(), 60),
                col("Est. cost", r -> Formatters.money(r.cost()), 100), badgeCol("Status", r -> r.status() == null ? "" : r.status().name(), 130));
        styleTable(table, Icons.APPROVALS, "Nothing waiting", "When an agent proposes an action it will appear here for review.");
        rationale.setEditable(false); rationale.setWrapText(true); rationale.setPrefRowCount(4);
        rationale.setPromptText("Select a request to read the agent's rationale");
        VBox why = card(sectionTitle("Rationale"), rationale);
        VBox body = new VBox(12, toolbar, flushCard(table), why); VBox.setVgrow(body.getChildren().get(1), Priority.ALWAYS); setCenter(body);

        var sel = table.getSelectionModel().selectedItemProperty();
        for (Button b : new Button[]{approve, reject, changes})
            b.disableProperty().bind(app.cannotApprove().or(Bindings.createBooleanBinding(
                    () -> sel.getValue() == null || sel.getValue().status() != ApprovalStatus.PENDING, sel)));
        sel.addListener((o, a, r) -> rationale.setText(r == null ? "" : (r.rationale() == null ? "" : r.rationale())
                + (r.decisionNote() == null || r.decisionNote().isBlank() ? "" : "\n\nDecision note: " + r.decisionNote())));
        history.selectedProperty().addListener((o, a, b) -> refresh());

        approve.setOnAction(e -> withSelected(table, r -> {
            if (Dlg.confirm("Approve and execute", r.action() + " · " + r.target() + "\nQuantity " + r.quantity() + " · estimated " + Formatters.money(r.cost())
                    + "\n\nThis creates real orders and cannot be undone.", "Approve & execute", false))
                act(() -> { app.bean(ApprovalService.class).approve(r.id(), app.userId(), null); return null; }, "Approved and executed"); }));
        reject.setOnAction(e -> withSelected(table, r -> Dlg.prompt("Reject request", "Reason (optional)", "")
                .ifPresent(n -> act(() -> { app.bean(ApprovalService.class).reject(r.id(), app.userId(), n); return null; }, "Request rejected"))));
        changes.setOnAction(e -> withSelected(table, r -> Dlg.prompt("Request changes", "What should the agent change?", "")
                .ifPresent(n -> act(() -> { app.bean(ApprovalService.class).requestChanges(r.id(), app.userId(), n); return null; }, "Changes requested"))));
    }

    @Override public void refresh() {
        Long keep = selected(table).map(ApprovalRow::id).orElse(null);
        load(() -> app.bean(ApprovalService.class).list(!history.isSelected()), l -> {
            table.setItems(FXCollections.observableArrayList(l));
            if (keep != null) l.stream().filter(r -> keep.equals(r.id())).findFirst().ifPresent(r -> table.getSelectionModel().select(r));
        });
    }
}
