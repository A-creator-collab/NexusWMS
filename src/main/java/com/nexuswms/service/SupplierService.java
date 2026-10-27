package com.nexuswms.service;

import com.nexuswms.model.*;
import com.nexuswms.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class SupplierService {
    /** Prior weight: a new supplier starts at 0.80 as if it had 5 completed orders of which 4 were on time. */
    static final int PRIOR_ON_TIME = 4, PRIOR_TOTAL = 5;

    private final SupplierRepository suppliers;
    private final PurchaseOrderRepository orders;
    private final AuditService audit;

    public SupplierService(SupplierRepository suppliers, PurchaseOrderRepository orders, AuditService audit) {
        this.suppliers = suppliers; this.orders = orders; this.audit = audit;
    }

    @Transactional(readOnly = true) public List<Supplier> listActive() { return suppliers.findByActiveTrueOrderByName(); }
    @Transactional(readOnly = true) public List<Supplier> listAll() { return suppliers.findAllByOrderByName(); }
    @Transactional(readOnly = true) public List<PurchaseOrder> history(Long supplierId) {
        return supplierId == null ? List.of() : orders.findBySupplierIdOrderByCreatedAtDescIdDesc(supplierId);
    }

    public SupplierForm toForm(Supplier s) {
        SupplierForm f = new SupplierForm();
        f.name = s.name; f.contactEmail = s.contactEmail; f.phone = s.phone; f.leadTimeDays = s.leadTimeDays;
        return f;
    }

    @Transactional
    public Supplier save(Long id, SupplierForm f, Long actor) {
        Validate.form(f);
        String name = f.name.trim();
        Supplier s = id == null ? new Supplier() : suppliers.findById(id).orElseThrow(() -> new BusinessException("Supplier not found."));
        boolean clash = suppliers.findByNameIgnoreCase(name).filter(o -> !o.id.equals(s.id)).isPresent();
        if (clash) throw new BusinessException("A supplier named '" + name + "' already exists.");
        s.name = name;
        s.contactEmail = f.contactEmail == null || f.contactEmail.isBlank() ? null : f.contactEmail.trim().toLowerCase();
        s.phone = f.phone == null || f.phone.isBlank() ? null : f.phone.trim();
        s.leadTimeDays = f.leadTimeDays;
        Supplier saved = suppliers.save(s);
        audit.log("SUPPLIER", saved.id, id == null ? "CREATE" : "UPDATE", actor, "name=" + saved.name);
        return saved;
    }

    /**
     * @return true when soft-deleted because historical orders reference the supplier.
     * A supplier with OPEN orders (draft, submitted, confirmed, receiving) can neither be deleted nor deactivated.
     */
    @Transactional
    public boolean delete(Long id, Long actor) {
        if (id == null) throw new BusinessException("No supplier selected.");
        Supplier s = suppliers.findById(id).orElseThrow(() -> new BusinessException("Supplier not found."));
        if (orders.existsBySupplierIdAndStatusIn(id, AnalyticsService.OPEN))
            throw new BusinessException("'" + s.name + "' still has open purchase orders. Cancel or receive them before removing the supplier.");
        if (orders.existsBySupplierId(id)) {
            s.active = false; suppliers.save(s);
            audit.log("SUPPLIER", id, "SOFT_DELETE", actor, "name=" + s.name);
            return true;
        }
        suppliers.delete(s);
        audit.log("SUPPLIER", id, "DELETE", actor, "name=" + s.name);
        return false;
    }

    /** score = (onTime + 4) / (completed + 5), on-time = fully received on or before the expected date. */
    public static BigDecimal score(int onTime, int completed) {
        return BigDecimal.valueOf(onTime + PRIOR_ON_TIME).divide(BigDecimal.valueOf(completed + PRIOR_TOTAL), 2, RoundingMode.HALF_UP);
    }

    @Transactional
    public BigDecimal recomputeReliability(Long supplierId) {
        Supplier s = suppliers.findById(supplierId).orElseThrow(() -> new BusinessException("Supplier not found."));
        int completed = 0, onTime = 0;
        for (PurchaseOrder o : orders.findBySupplierIdOrderByCreatedAtDescIdDesc(supplierId)) {
            if (o.status != OrderStatus.RECEIVED || o.expectedDate == null) continue;
            completed++;
            if (!o.updatedAt.toLocalDate().isAfter(o.expectedDate)) onTime++;
        }
        s.reliabilityScore = score(onTime, completed);
        suppliers.save(s);
        return s.reliabilityScore;
    }
}
