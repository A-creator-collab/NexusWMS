package com.nexuswms.service;

import org.slf4j.*;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs side effects (user notifications) only once the surrounding transaction has COMMITTED.
 * Without this, a rolled-back receive/approval would still pop a "success" toast.
 * Outside a transaction (e.g. unit tests) the action runs immediately.
 */
public final class AfterCommit {
    private static final Logger log = LoggerFactory.getLogger(AfterCommit.class);
    private AfterCommit() {}

    public static void run(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { safe(action); }
            });
        } else safe(action);
    }

    private static void safe(Runnable r) { try { r.run(); } catch (RuntimeException e) { log.warn("after-commit action failed", e); } }
}
