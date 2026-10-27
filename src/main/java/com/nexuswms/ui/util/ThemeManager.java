package com.nexuswms.ui.util;

import atlantafx.base.theme.*;
import javafx.scene.Parent;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.prefs.Preferences;

/** Light/dark AtlantaFX theme (persisted). Also flips the ".dark" class on every registered root so styles.css tokens follow. */
public final class ThemeManager {
    private static final Preferences PREFS = Preferences.userNodeForPackage(ThemeManager.class);
    private static final List<WeakReference<Parent>> ROOTS = new ArrayList<>();
    private ThemeManager() {}

    public static boolean isDark() {
        String env = System.getenv("NEXUS_UI_THEME");
        return PREFS.getBoolean("dark", env != null && env.equalsIgnoreCase("PrimerDark"));
    }

    /** Applies the AtlantaFX base theme and refreshes all registered roots. */
    public static void apply() {
        javafx.application.Application.setUserAgentStylesheet((isDark() ? new PrimerDark() : new PrimerLight()).getUserAgentStylesheet());
        boolean dark = isDark();
        synchronized (ROOTS) {
            ROOTS.removeIf(r -> r.get() == null);
            for (WeakReference<Parent> r : ROOTS) { Parent p = r.get(); if (p != null) mark(p, dark); }
        }
    }

    /** Register a scene/dialog root so it follows theme changes. */
    public static void decorate(Parent root) {
        synchronized (ROOTS) { ROOTS.add(new WeakReference<>(root)); }
        mark(root, isDark());
    }

    public static void toggle() { PREFS.putBoolean("dark", !isDark()); apply(); }

    private static void mark(Parent p, boolean dark) {
        p.getStyleClass().remove("dark");
        if (dark) p.getStyleClass().add("dark");
    }
}
