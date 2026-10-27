package com.nexuswms.ui.components;

import javafx.scene.control.Label;

/** Coloured pill. kind: ok | warn | danger | info | neutral (styled in styles.css). */
public class StatusBadge extends Label {
    public StatusBadge(String text, String kind) { super(text); set(text, kind); }
    public void set(String text, String kind) {
        setText(text);
        getStyleClass().removeIf(s -> s.startsWith("badge"));
        getStyleClass().addAll("badge", "badge-" + kind);
    }
    public static String kindForStatus(String s) {
        if (s == null) return "neutral";
        return switch (s) { case "OK", "RECEIVED", "APPROVED", "RECEIVE" -> "ok"; case "LOW", "RECEIVING", "RETURN", "PENDING", "CHANGES_REQUESTED" -> "warn";
            case "OUT", "CANCELLED", "REJECTED", "SHRINK" -> "danger"; case "SHIP", "SUBMITTED", "CONFIRMED" -> "info"; default -> "neutral"; };
    }
}
