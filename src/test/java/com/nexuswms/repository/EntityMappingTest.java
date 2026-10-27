package com.nexuswms.repository;

import com.nexuswms.agents.*;
import com.nexuswms.model.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Schema/entity guard. Runs the REAL schema.sql on H2 (MySQL mode), then round-trips all nine tables plus approval_requests through
 * the entities: any column-name or type mismatch fails here, before you ever start the app against MySQL.
 * (Hibernate's own ddl-auto=validate is applied only against MySQL at runtime - H2 reports ENUM/TEXT metadata differently.)
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:nexus;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:database/schema.sql"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EntityMappingTest {
    @Autowired EntityManager em;
    @Autowired InventoryRepository inventory;
    @Autowired ProductRepository products;
    @Autowired ApprovalRequestRepository approvals;

    @Test void allEntitiesRoundTrip() {
        User u = new User(); u.username = "tester"; u.passwordHash = "x"; u.role = Role.ADMIN; em.persist(u);
        Supplier s = new Supplier(); s.name = "Acme"; s.contactEmail = "a@b.c"; em.persist(s);
        Product p = new Product(); p.sku = "SKU-T"; p.name = "Test"; p.category = "Cat"; p.description = "d"; p.unitPrice = new BigDecimal("3.50");
        p.reorderPoint = 10; p.safetyStock = 2; p.supplier = s; em.persist(p);
        Inventory inv = new Inventory(); inv.product = p; inv.quantityOnHand = 4; inv.binLocation = "A-01"; em.persist(inv);
        PurchaseOrder o = new PurchaseOrder(); o.orderNumber = "PO-T1"; o.supplier = s; o.status = OrderStatus.CONFIRMED; o.expectedDate = LocalDate.now();
        o.createdBy = u.id; OrderItem it = new OrderItem(); it.order = o; it.product = p; it.quantityOrdered = 5; it.unitCost = BigDecimal.ONE; o.items.add(it); em.persist(o);
        StockMovement m = new StockMovement(); m.product = p; m.delta = 4; m.reason = MovementReason.RECEIVE; m.refOrderId = o.id; m.actorUserId = u.id; m.note = "n"; em.persist(m);
        Forecast f = new Forecast(); f.product = p; f.forecastDate = LocalDate.now(); f.predictedDemand = new BigDecimal("12.34"); f.method = "EXP_SMOOTH"; f.confidence = new BigDecimal("0.95"); em.persist(f);
        AuditLog a = new AuditLog(); a.entityType = "PRODUCT"; a.entityId = p.id; a.action = "CREATE"; a.actorUserId = u.id; a.details = "x"; em.persist(a);
        ApprovalRequest r = new ApprovalRequest(); r.agent = "T"; r.action = AgentAction.CREATE_PO; r.productId = p.id; r.quantity = 3; r.dedupKey = "PO:" + p.id; r.rationale = "why"; em.persist(r);
        em.flush(); em.clear();

        assertEquals("tester", em.find(User.class, u.id).username);
        assertEquals(Role.ADMIN, em.find(User.class, u.id).role);
        assertEquals(1, em.find(PurchaseOrder.class, o.id).items.size());
        assertEquals(OrderStatus.CONFIRMED, em.find(PurchaseOrder.class, o.id).status);
        assertEquals(MovementReason.RECEIVE, em.find(StockMovement.class, m.id).reason);
        assertEquals(new BigDecimal("12.34"), em.find(Forecast.class, f.id).predictedDemand);
        assertEquals("x", em.find(AuditLog.class, a.id).details);
        assertEquals(ApprovalStatus.PENDING, em.find(ApprovalRequest.class, r.id).status);
        assertNotNull(em.find(Supplier.class, s.id).createdAt);
    }

    @Test void inventoryQueriesWork() {
        Product p = new Product(); p.sku = "SKU-Q"; p.name = "Query Me"; p.category = "Zed"; p.reorderPoint = 10; em.persist(p);
        Inventory i = new Inventory(); i.product = p; i.quantityOnHand = 3; em.persist(i); em.flush(); em.clear();
        assertEquals(1, inventory.search("%query%", PageRequest.of(0, 10)).getTotalElements());
        assertEquals(0, inventory.search("%nomatch%", PageRequest.of(0, 10)).getTotalElements());
        assertTrue(inventory.findLowStock().stream().anyMatch(x -> x.product.sku.equals("SKU-Q")));
        assertTrue(inventory.findForUpdate(p.id).isPresent());
        assertTrue(products.existsBySkuIgnoreCase("sku-q"));
        assertEquals(0, approvals.countByStatus(ApprovalStatus.APPROVED));
    }

    @Test void checkConstraintBlocksNegativeStock() {
        Product p = new Product(); p.sku = "SKU-N"; p.name = "Neg"; em.persist(p);
        Inventory i = new Inventory(); i.product = p; i.quantityOnHand = -1; em.persist(i);
        assertThrows(Exception.class, () -> em.flush());
    }
}
