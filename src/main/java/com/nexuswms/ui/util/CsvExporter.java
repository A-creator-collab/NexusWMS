package com.nexuswms.ui.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** RFC-4180 CSV writer (UTF-8 with BOM so Excel opens it correctly) with spreadsheet formula-injection protection. */
public final class CsvExporter {
    private CsvExporter() {}

    public static void write(Path file, List<String> header, List<List<String>> rows) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write('\uFEFF');
            w.write(line(header));
            for (List<String> r : rows) w.write(line(r));
        }
    }

    static String line(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) { if (i > 0) sb.append(','); sb.append(cell(cells.get(i))); }
        return sb.append("\r\n").toString();
    }

    static String cell(String value) {
        String s = value == null ? "" : value;
        if (isFormula(s)) s = "'" + s;                                  // a cell starting with = + @ (or -text) would execute in Excel
        boolean quote = s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        s = s.replace("\"", "\"\"");
        return quote ? "\"" + s + "\"" : s;
    }

    private static boolean isFormula(String s) {
        if (s.isEmpty()) return false;
        char c = s.charAt(0);
        if (c == '=' || c == '+' || c == '@' || c == '\t' || c == '\r') return true;
        return c == '-' && s.length() > 1 && !Character.isDigit(s.charAt(1)) && s.charAt(1) != '.';   // "-5" is a number, "-cmd" is not
    }
}
