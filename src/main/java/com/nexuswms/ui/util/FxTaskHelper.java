package com.nexuswms.ui.util;

import com.nexuswms.service.BusinessException;
import javafx.application.Platform;
import javafx.concurrent.Task;
import org.controlsfx.control.Notifications;
import org.slf4j.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** All I/O runs in a Task on a worker pool; all UI updates happen on the FX thread. Errors become notifications, never stack traces. */
public final class FxTaskHelper {
    private static final Logger log = LoggerFactory.getLogger(FxTaskHelper.class);
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "nexus-io"); t.setDaemon(true); return t; });
    private FxTaskHelper() {}

    public static <T> void run(Callable<T> work, Consumer<T> onOk) { run(work, onOk, FxTaskHelper::error); }

    public static <T> void run(Callable<T> work, Consumer<T> onOk, Consumer<Throwable> onErr) {
        Task<T> task = new Task<>() { @Override protected T call() throws Exception { return work.call(); } };
        task.setOnSucceeded(e -> { try { onOk.accept(task.getValue()); } catch (RuntimeException ex) { error(ex); } });
        task.setOnFailed(e -> onErr.accept(task.getException()));
        POOL.submit(task);
    }

    /** Run on the FX thread (immediately if already there). */
    public static void ui(Runnable r) { if (Platform.isFxApplicationThread()) r.run(); else Platform.runLater(r); }

    public static void error(Throwable ex) {
        log.error("Operation failed", ex);
        Throwable root = ex; while (root.getCause() != null) root = root.getCause();
        String msg = ex instanceof BusinessException ? ex.getMessage()
                : (root instanceof java.sql.SQLException || root.getClass().getName().contains("jdbc"))
                ? "The database is not reachable or rejected the request. Details are in the log."
                : "Something went wrong. Details are in the log.";
        ui(() -> Notifications.create().title("Error").text(msg).showError());
    }

    public static void info(String title, String text) { ui(() -> Notifications.create().title(title).text(text).showInformation()); }
    public static void warn(String title, String text) { ui(() -> Notifications.create().title(title).text(text).showWarning()); }
}
