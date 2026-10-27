package com.nexuswms.ui;

import com.nexuswms.security.SessionContext;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.*;
import org.springframework.context.ConfigurableApplicationContext;

/** What the UI knows about the running backend. spring==null means offline shell. */
public class AppContext {
    public final ConfigurableApplicationContext spring;
    public final String offlineReason;
    public final BooleanProperty dbUp = new SimpleBooleanProperty(false);
    private final BooleanBinding readOnly;
    private final BooleanBinding cannotApprove;

    public AppContext(ConfigurableApplicationContext spring, String offlineReason) {
        this.spring = spring; this.offlineReason = offlineReason;
        dbUp.set(spring != null);
        readOnly = Bindings.createBooleanBinding(() -> !dbUp.get() || !session().canMutate(), dbUp);
        cannotApprove = Bindings.createBooleanBinding(() -> !dbUp.get() || !session().canApprove(), dbUp);
    }
    public boolean online() { return spring != null; }
    public <T> T bean(Class<T> c) { return spring.getBean(c); }
    public SessionContext session() { return online() ? bean(SessionContext.class) : new SessionContext(); }
    public Long userId() { return session().userId(); }
    /** True while the DB is down or the signed-in role cannot mutate. Bind buttons' disable property to this. */
    public BooleanBinding readOnly() { return readOnly; }
    public BooleanBinding cannotApprove() { return cannotApprove; }
}
