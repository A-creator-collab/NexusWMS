package com.nexuswms.ui.util;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.kordamp.ikonli.javafx.FontIcon;

/** Font Awesome 5 (Ikonli) icon literals + safe factory (a bad literal shows a dot instead of crashing the screen). */
public final class Icons {
    public static final String DASHBOARD = "fas-th-large", INVENTORY = "fas-boxes", ORDERS = "fas-shopping-cart", SUPPLIERS = "fas-truck",
            MAP = "fas-map-marked-alt", ANALYTICS = "fas-chart-line", AGENTS = "fas-robot", APPROVALS = "fas-check-circle",
            AUDIT = "fas-clipboard-list", SETTINGS = "fas-cog", MIC = "fas-microphone", BELL = "fas-bell", MOON = "fas-moon", SUN = "fas-sun",
            SEARCH = "fas-search", PLUS = "fas-plus", EDIT = "fas-pen", TRASH = "fas-trash-alt", WAREHOUSE = "fas-warehouse",
            WARNING = "fas-exclamation-triangle", REFRESH = "fas-sync-alt", SEND = "fas-paper-plane", USER = "fas-user", CLOSE = "fas-times",
            DATABASE = "fas-database", PLAY = "fas-play", BOX = "fas-box", DOLLAR = "fas-dollar-sign", INFO = "fas-info-circle",
            CHECK = "fas-check", INBOX = "fas-inbox", SLIDERS = "fas-sliders-h", TRUCK_LOADING = "fas-dolly", HISTORY = "fas-history",
            BAN = "fas-ban", COMMENT = "fas-comment-dots", EXCHANGE = "fas-exchange-alt", FILE = "fas-file-alt", STOP = "fas-stop-circle";

    private Icons() {}

    public static Node of(String literal) { return of(literal, 16); }

    public static Node of(String literal, int size) {
        try { FontIcon f = new FontIcon(literal); f.setIconSize(size); return f; }
        catch (RuntimeException e) { return new Label("•"); }
    }

    /** Rounded icon chip; variant = accent | success | warning | info. Styled by .kpi-chip in styles.css. */
    public static StackPane chip(String literal, String variant, int size) {
        StackPane p = new StackPane(of(literal, size));
        p.getStyleClass().addAll("kpi-chip", variant);
        return p;
    }
}
