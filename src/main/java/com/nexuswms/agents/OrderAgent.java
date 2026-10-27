package com.nexuswms.agents;

import com.nexuswms.model.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Proposes order status transitions only when evidence supports them (idle drafts, badly overdue orders from unreliable suppliers). */
@Component @Order(3)
public class OrderAgent implements Agent {
    @Override public String name() { return "OrderAgent"; }
    @Override public String settingsKey() { return "order"; }

    @Override
    public List<ProposedAction> propose(AgentContext ctx) {
        List<ProposedAction> out = new ArrayList<>();
        for (PurchaseOrder o : ctx.openOrders()) {
            long idle = ChronoUnit.DAYS.between(o.createdAt.toLocalDate(), ctx.today());
            if (o.status == OrderStatus.DRAFT && idle >= 2) {
                out.add(new ProposedAction(name(), AgentAction.ORDER_STATUS_CHANGE, null, o.supplier.id, o.id, 0, o.totalAmount, "SUBMITTED",
                        o.orderNumber + " has been a draft for " + idle + " days; supplier lead time is " + o.supplier.leadTimeDays + " days. Submit to avoid delay."));
            } else if ((o.status == OrderStatus.SUBMITTED || o.status == OrderStatus.CONFIRMED) && o.expectedDate != null
                    && o.expectedDate.plusDays(3).isBefore(ctx.today()) && o.supplier.reliabilityScore.doubleValue() < 0.80) {
                out.add(new ProposedAction(name(), AgentAction.ORDER_STATUS_CHANGE, null, o.supplier.id, o.id, 0, o.totalAmount, "CANCELLED",
                        o.orderNumber + " is more than 3 days past its expected date " + o.expectedDate + " and " + o.supplier.name
                                + " has reliability " + o.supplier.reliabilityScore + ". Cancel and re-source."));
            }
        }
        return out;
    }
}
