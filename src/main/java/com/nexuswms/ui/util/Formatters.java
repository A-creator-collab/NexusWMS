package com.nexuswms.ui.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class Formatters {
    private Formatters() {}
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    public static String money(BigDecimal v) { return v == null ? "" : NumberFormat.getCurrencyInstance(Locale.US).format(v); }
    public static String money(double v) { return NumberFormat.getCurrencyInstance(Locale.US).format(v); }
    public static String dateTime(LocalDateTime t) { return t == null ? "" : t.format(DT); }
    public static String date(LocalDate d) { return d == null ? "" : d.toString(); }
    public static String signed(int n) { return n > 0 ? "+" + n : String.valueOf(n); }
}
