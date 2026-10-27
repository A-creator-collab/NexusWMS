package com.nexuswms.ui;

import com.nexuswms.service.AuditService;
import com.nexuswms.service.AuditService.AuditRow;
import com.nexuswms.ui.util.Formatters;
import com.nexuswms.ui.util.Icons;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class AuditView extends BaseView {
    private final TableView<AuditRow> table = new TableView<>();
    private final ChoiceBox<String> type = new ChoiceBox<>();
    private final TextField actor = new TextField();
    private final DatePicker from = new DatePicker(), to = new DatePicker();
    private final Button apply = new Button("Apply"), export = button("Export CSV", Icons.FILE, null);

    public AuditView(AppContext app) {
        super(app);
        type.getItems().addAll("ALL", "PRODUCT", "INVENTORY", "ORDER", "SUPPLIER", "FORECAST", "APPROVAL", "AUTH", "SETTINGS"); type.setValue("ALL");
        actor.setPromptText("Actor contains…"); from.setPromptText("From"); to.setPromptText("To");
        setPageHeader("Audit log", "Every change, who made it and when", type, actor, from, to, apply, export);
        table.getColumns().addAll(col("When", (AuditRow r) -> Formatters.dateTime(r.at()), 135), col("Entity", AuditRow::entityType, 90),
                col("ID", r -> r.entityId() == null ? "" : r.entityId().toString(), 60), col("Action", AuditRow::action, 90),
                col("Actor", AuditRow::actor, 90), col("Details", AuditRow::details, 520));
        styleTable(table, com.nexuswms.ui.util.Icons.AUDIT, "No audit entries match", "Adjust the filters or widen the date range.");
        setCenter(flushCard(table));
        apply.setOnAction(e -> refresh());
        export.disableProperty().bind(app.dbUp.not());
        export.setOnAction(e -> { var snapshot = new java.util.ArrayList<>(table.getItems());
            exportCsv("audit-" + java.time.LocalDate.now() + ".csv", List.of("When", "Entity", "ID", "Action", "Actor", "Details"),
                () -> snapshot.stream().map(r -> List.of(Formatters.dateTime(r.at()), r.entityType(), r.entityId() == null ? "" : r.entityId().toString(),
                        r.action(), r.actor(), r.details() == null ? "" : r.details())).toList()); });
    }

    @Override public void refresh() {
        load(() -> app.bean(AuditService.class).search(from.getValue(), to.getValue(), type.getValue() == null ? "ALL" : type.getValue(), actor.getText() == null ? "" : actor.getText(), 500), l -> table.setItems(FXCollections.observableArrayList(l)));
    }
}
