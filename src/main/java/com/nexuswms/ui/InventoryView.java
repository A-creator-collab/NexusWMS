package com.nexuswms.ui;

import com.nexuswms.model.Supplier;
import com.nexuswms.service.*;
import com.nexuswms.service.InventoryService.*;
import com.nexuswms.ui.components.*;
import com.nexuswms.ui.util.*;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.springframework.data.domain.Page;
import java.util.List;

public class InventoryView extends BaseView {
    private record Result(Page<InventoryRow> page, List<Supplier> suppliers) {}
    private static final int PAGE_SIZE = 20;
    private final TableView<InventoryRow> table = new TableView<>();
    private final TextField search = new TextField();
    private final Debouncer debounce = new Debouncer(300);
    private final Label pageLabel = new Label();
    private final Button prev = button("Previous", null, "ghost"), next = button("Next", null, "ghost");
    private final Button add = button("Add product", Icons.PLUS, "primary"), edit = button("Edit", Icons.EDIT, null),
            adjust = button("Adjust stock", Icons.EXCHANGE, null), del = button("Delete", Icons.TRASH, null),
            export = button("Export CSV", Icons.FILE, null);
    private final TableView<MovementRow> ledger = new TableView<>();
    private final Label ledgerTitle = sectionTitle("Stock ledger");
    private int page = 0, pages = 1;
    private long requestSeq;                                  // ignores out-of-order responses while the user types
    private List<Supplier> suppliers = List.of();

    public InventoryView(AppContext app) {
        super(app);
        setPageHeader("Inventory", "Stock levels, locations and replenishment status", add);
        search.setPromptText("Search by SKU, name or category…");
        HBox searchBox = new HBox(8, Icons.of(Icons.SEARCH, 13), search); searchBox.getStyleClass().add("search-box"); searchBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(search, Priority.ALWAYS); searchBox.setPrefWidth(360);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        HBox toolbar = new HBox(8, searchBox, sp, export, edit, adjust, del); toolbar.getStyleClass().add("toolbar"); toolbar.setPadding(new Insets(0, 0, 4, 0));

        table.getColumns().addAll(col("SKU", (InventoryRow r) -> r.sku(), 90), col("Name", InventoryRow::name, 200), col("Category", InventoryRow::category, 110),
                col("On hand", r -> "" + r.onHand(), 75), col("Reserved", r -> "" + r.reserved(), 80), col("Available", r -> "" + r.available(), 80),
                col("Reorder pt", r -> "" + r.reorderPoint(), 85), badgeCol("Status", InventoryRow::status, 90), col("Bin", InventoryRow::bin, 70),
                col("Supplier", InventoryRow::supplier, 150));
        styleTable(table, Icons.INVENTORY, "No products found", "Try a different search, or add your first product.");

        HBox pager = new HBox(10, pageLabel, new Region(), prev, next); pager.setAlignment(Pos.CENTER_LEFT); pager.setPadding(new Insets(10, 14, 10, 14));
        HBox.setHgrow(pager.getChildren().get(1), Priority.ALWAYS); pageLabel.getStyleClass().add("muted");
        VBox tableCard = flushCard(table); tableCard.getChildren().add(pager);
        VBox left = new VBox(12, toolbar, tableCard); VBox.setVgrow(tableCard, Priority.ALWAYS);

        ledger.getColumns().addAll(col("When", (MovementRow m) -> Formatters.dateTime(m.at()), 120), col("Change", m -> Formatters.signed(m.delta()), 65),
                badgeCol("Type", MovementRow::reason, 95), col("Note", MovementRow::note, 140));
        styleTable(ledger, Icons.HISTORY, "No product selected", "Select a row to see its last 50 movements.");
        Label ledgerSub = new Label("Last 50 movements"); ledgerSub.getStyleClass().add("section-sub");
        VBox drawer = new VBox(4, ledgerTitle, ledgerSub, ledger); drawer.getStyleClass().add("card"); drawer.setPrefWidth(400); drawer.setMinWidth(400);
        VBox.setVgrow(ledger, Priority.ALWAYS);
        HBox body = new HBox(16, left, drawer); HBox.setHgrow(left, Priority.ALWAYS); setCenter(body);

        export.disableProperty().bind(app.dbUp.not());
        export.setOnAction(e -> { String q = search.getText(); exportCsv("inventory-" + java.time.LocalDate.now() + ".csv",
                List.of("SKU", "Name", "Category", "On hand", "Reserved", "Available", "Reorder point", "Status", "Bin", "Supplier"),
                () -> app.bean(InventoryService.class).exportAll(q).stream().map(r -> List.of(r.sku(), r.name(), r.category() == null ? "" : r.category(),
                        String.valueOf(r.onHand()), String.valueOf(r.reserved()), String.valueOf(r.available()), String.valueOf(r.reorderPoint()), r.status(),
                        r.bin() == null ? "" : r.bin(), r.supplier() == null ? "" : r.supplier())).toList()); });
        add.disableProperty().bind(app.readOnly());
        var noSelection = table.getSelectionModel().selectedItemProperty().isNull();
        edit.disableProperty().bind(app.readOnly().or(noSelection)); adjust.disableProperty().bind(app.readOnly().or(noSelection)); del.disableProperty().bind(app.readOnly().or(noSelection));
        search.textProperty().addListener((o, a, b) -> debounce.submit(() -> { page = 0; refresh(); }));
        prev.setOnAction(e -> { if (page > 0) { page--; refresh(); } });
        next.setOnAction(e -> { if (page + 1 < pages) { page++; refresh(); } });
        table.getSelectionModel().selectedItemProperty().addListener((o, a, r) -> showLedger(r));

        add.setOnAction(e -> ProductDialog.show(suppliers, null).ifPresent(f -> act(() -> app.bean(ProductService.class).create(f, app.userId()), "Product created")));
        edit.setOnAction(e -> withSelected(table, r -> load(() -> app.bean(ProductService.class).getForm(r.productId()),
                form -> ProductDialog.show(suppliers, form).ifPresent(f -> act(() -> app.bean(ProductService.class).update(r.productId(), f, app.userId()), "Product updated")))));
        adjust.setOnAction(e -> withSelected(table, r -> AdjustDialog.show(r.sku() + " · " + r.name(), r.onHand()).ifPresent(a -> act(
                () -> app.bean(InventoryService.class).adjustStock(r.productId(), a.delta(), a.reason(), app.userId(), a.note()), n -> "New on-hand quantity: " + n))));
        del.setOnAction(e -> withSelected(table, r -> {
            if (Dlg.confirm("Delete product", "Delete " + r.sku() + " – " + r.name() + "?\nProducts with history are deactivated instead of removed.", "Delete", true))
                act(() -> app.bean(ProductService.class).delete(r.productId(), app.userId()), soft -> soft ? "Product deactivated (it has history)" : "Product deleted");
        }));
    }

    /** Used by the global search box in the top bar. */
    public void setQuery(String q) { search.setText(q == null ? "" : q); }

    private void showLedger(InventoryRow r) {
        if (r == null) { ledger.getItems().clear(); ledgerTitle.setText("Stock ledger"); return; }
        ledgerTitle.setText(r.sku() + " · ledger");
        load(() -> app.bean(InventoryService.class).movements(r.productId()), m -> ledger.setItems(FXCollections.observableArrayList(m)));
    }

    @Override public void refresh() {
        final long seq = ++requestSeq; final String q = search.getText(); final int p = page;
        load(() -> new Result(app.bean(InventoryService.class).list(q, p, PAGE_SIZE), app.bean(SupplierService.class).listActive()), r -> {
            if (seq != requestSeq) return;                  // a newer search superseded this response
            suppliers = r.suppliers(); pages = Math.max(1, r.page().getTotalPages());
            if (page >= pages) { page = pages - 1; }
            table.setItems(FXCollections.observableArrayList(r.page().getContent()));
            pageLabel.setText(r.page().getTotalElements() + " item(s) · page " + (page + 1) + " of " + pages);
            prev.setDisable(page == 0); next.setDisable(page + 1 >= pages);
        });
    }
}
