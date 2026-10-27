package com.nexuswms.service;

import com.nexuswms.model.*;
import com.nexuswms.repository.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class InventoryService {
    public static final int MAX_ADJUST = 10_000_000, MAX_STOCK = 100_000_000, MAX_NOTE = 500;

    public record InventoryRow(Long productId, String sku, String name, String category, int onHand, int reserved,
                               int available, int reorderPoint, String bin, String supplier, String status) {}
    public record MovementRow(LocalDateTime at, String sku, String product, int delta, String reason, String note) {}
    /** One warehouse bin on the map. Capacity is modelled as 4x the reorder point (documented in README). */
    public record BinCell(String bin, Long productId, String sku, String product, int onHand, int capacity, double ratio) {}

    private final InventoryRepository inv;
    private final StockMovementRepository mov;
    private final AuditService audit;
    private final NotificationService notes;

    public InventoryService(InventoryRepository inv, StockMovementRepository mov, AuditService audit, NotificationService notes) {
        this.inv = inv; this.mov = mov; this.audit = audit; this.notes = notes;
    }

    static String status(int available, int reorderPoint) {
        return available <= 0 ? "OUT" : available <= reorderPoint ? "LOW" : "OK";
    }

    static InventoryRow row(Inventory i) {
        Product p = i.product;
        return new InventoryRow(p.id, p.sku, p.name, p.category, i.quantityOnHand, i.quantityReserved, i.available(),
                p.reorderPoint, i.binLocation, p.supplier == null ? "" : p.supplier.name, status(i.available(), p.reorderPoint));
    }

    @Transactional(readOnly = true)
    public Page<InventoryRow> list(String q, int page, int size) {
        if (page < 0 || size < 1 || size > 500) throw new BusinessException("Invalid page request.");
        String like = "%" + (q == null ? "" : q.trim().toLowerCase()) + "%";
        return inv.search(like, PageRequest.of(page, size)).map(InventoryService::row);
    }

    /** Everything matching the search (capped at 50,000 rows) for CSV export. */
    @Transactional(readOnly = true)
    public List<InventoryRow> exportAll(String q) {
        String like = "%" + (q == null ? "" : q.trim().toLowerCase()) + "%";
        return inv.search(like, PageRequest.of(0, 50_000)).map(InventoryService::row).getContent();
    }

    @Transactional(readOnly = true)
    public List<InventoryRow> lowStock() { return inv.findLowStock().stream().map(InventoryService::row).toList(); }

    @Transactional
    public int adjustStock(Long productId, int delta, MovementReason reason, Long actorId, String note) {
        return adjustStock(productId, delta, reason, actorId, note, null);
    }

    /**
     * ONE transaction: row-lock the inventory record, update the quantity, append the ledger row, write the audit row.
     * Rules: non-zero; bounded; direction must match the reason (RECEIVE/RETURN add, SHIP/SHRINK remove); never below zero;
     * never below the reserved quantity; inactive products are frozen.
     */
    @Transactional
    public int adjustStock(Long productId, int delta, MovementReason reason, Long actorId, String note, Long refOrderId) {
        if (productId == null) throw new BusinessException("No product selected.");
        if (reason == null) throw new BusinessException("A reason is required for every stock movement.");
        if (delta == 0) throw new BusinessException("Adjustment must be a non-zero quantity.");
        if (Math.abs((long) delta) > MAX_ADJUST) throw new BusinessException("A single adjustment may not exceed " + MAX_ADJUST + " units.");
        boolean mustAdd = reason == MovementReason.RECEIVE || reason == MovementReason.RETURN;
        boolean mustRemove = reason == MovementReason.SHIP || reason == MovementReason.SHRINK;
        if (mustAdd && delta < 0) throw new BusinessException(reason + " must increase stock; use SHIP, SHRINK or ADJUST to remove units.");
        if (mustRemove && delta > 0) throw new BusinessException(reason + " must decrease stock; use RECEIVE, RETURN or ADJUST to add units.");
        if (note != null && note.length() > MAX_NOTE) throw new BusinessException("Note is too long (max " + MAX_NOTE + " characters).");

        Inventory i = inv.findForUpdate(productId).orElseThrow(() -> new BusinessException("No inventory record for product " + productId));
        if (!i.product.active) throw new BusinessException(i.product.sku + " is deactivated; stock can no longer be changed.");
        int before = i.quantityOnHand;
        long after = (long) before + delta;
        if (after < 0) throw new BusinessException("Stock cannot go below zero (on hand " + before + ", change " + delta + ").");
        if (after > MAX_STOCK) throw new BusinessException("Resulting stock exceeds the supported maximum of " + MAX_STOCK + ".");
        if (after < i.quantityReserved) throw new BusinessException("Cannot reduce stock to " + after + ": " + i.quantityReserved + " units are reserved.");

        i.quantityOnHand = (int) after;
        inv.save(i);
        StockMovement m = new StockMovement();
        m.product = i.product; m.delta = delta; m.reason = reason; m.refOrderId = refOrderId; m.actorUserId = actorId;
        m.note = note == null || note.isBlank() ? null : note.trim();
        mov.save(m);
        audit.log("INVENTORY", i.id, "ADJUST", actorId, "sku=" + i.product.sku + " delta=" + delta + " reason=" + reason
                + " before=" + before + " after=" + after);
        int rp = i.product.reorderPoint, reserved = i.quantityReserved;
        String sku = i.product.sku; int afterInt = (int) after;
        if (before - reserved > rp && afterInt - reserved <= rp)
            AfterCommit.run(() -> notes.publish("WARN", sku + " crossed its reorder point (" + afterInt + " on hand)."));
        return afterInt;
    }

    @Transactional(readOnly = true)
    public List<MovementRow> movements(Long productId) {
        if (productId == null) return List.of();
        return mov.findByProductIdOrderByCreatedAtDescIdDesc(productId, PageRequest.of(0, 50)).stream().map(InventoryService::mrow).toList();
    }

    @Transactional(readOnly = true)
    public List<MovementRow> recent(int n) {
        return mov.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, Math.max(1, Math.min(n, 200)))).stream().map(InventoryService::mrow).toList();
    }

    static MovementRow mrow(StockMovement m) {
        return new MovementRow(m.createdAt, m.product.sku, m.product.name, m.delta, m.reason.name(), m.note);
    }

    @Transactional(readOnly = true)
    public BigDecimal totalValue() {
        BigDecimal v = BigDecimal.ZERO;
        for (Inventory i : inv.findAllActive()) v = v.add(i.product.unitPrice.multiply(BigDecimal.valueOf(i.quantityOnHand)));
        return v;
    }

    @Transactional(readOnly = true)
    public List<BinCell> mapBins() {
        List<BinCell> out = new ArrayList<>();
        for (Inventory i : inv.findAllActive()) {
            int cap = Math.max(1, i.product.reorderPoint * 4);
            out.add(new BinCell(i.binLocation == null ? "-" : i.binLocation, i.product.id, i.product.sku, i.product.name,
                    i.quantityOnHand, cap, Math.min(1.0, (double) i.quantityOnHand / cap)));
        }
        return out;
    }
}
