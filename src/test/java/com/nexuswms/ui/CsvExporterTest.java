package com.nexuswms.ui;

import com.nexuswms.ui.util.CsvExporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CsvExporterTest {
    @Test void writesQuotedEscapedAndBomPrefixedCsv(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("out.csv");
        CsvExporter.write(f, List.of("SKU", "Name"), List.of(List.of("A-1", "Box, large \"XL\""), List.of("A-2", "Line\nbreak"), List.of("A-3", "")));
        String text = Files.readString(f);
        assertTrue(text.startsWith("\uFEFFSKU,Name\r\n"));
        assertTrue(text.contains("A-1,\"Box, large \"\"XL\"\"\"\r\n"));
        assertTrue(text.contains("A-2,\"Line\nbreak\"\r\n")); assertTrue(text.contains("A-3,\r\n"));
    }
    @Test void spreadsheetFormulasAreNeutralised(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("x.csv");
        CsvExporter.write(f, List.of("v"), List.of(List.of("=cmd|' /C calc'!A0"), List.of("+1+1"), List.of("@SUM(A1)"), List.of("-cmd"), List.of("-5"), List.of("-0.5")));
        String text = Files.readString(f);
        assertTrue(text.contains("'=cmd")); assertTrue(text.contains("'+1+1")); assertTrue(text.contains("'@SUM")); assertTrue(text.contains("'-cmd"));
        assertTrue(text.contains("\r\n-5\r\n")); assertTrue(text.contains("\r\n-0.5\r\n"));      // real negative numbers are untouched
    }
}
