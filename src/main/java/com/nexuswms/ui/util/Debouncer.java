package com.nexuswms.ui.util;

import javafx.animation.PauseTransition;
import javafx.util.Duration;

public class Debouncer {
    private final PauseTransition pause;
    public Debouncer(long millis) { pause = new PauseTransition(Duration.millis(millis)); }
    public void submit(Runnable r) { pause.setOnFinished(e -> r.run()); pause.playFromStart(); }
}
