package com.nexuswms.voice;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpokenNormalizerTest {
    @Test void digitRunsAreReadDigitByDigit() { assertEquals("sku-1023", SpokenNormalizer.normalize("sku one zero two three")); }
    @Test void cardinalsAreComputed() {
        assertEquals("for 25 of", SpokenNormalizer.normalize("for twenty five of"));
        assertEquals("120", SpokenNormalizer.normalize("one hundred twenty"));
        assertEquals("2500", SpokenNormalizer.normalize("two thousand five hundred"));
        assertEquals("13", SpokenNormalizer.normalize("thirteen"));
    }
    @Test void spelledSkuAndDashWords() {
        assertEquals("sku-1001", SpokenNormalizer.normalize("S K U one zero zero one"));
        assertEquals("sku-1001", SpokenNormalizer.normalize("sku dash one zero zero one"));
    }
    @Test void ohIsZeroInDigitRuns() { assertEquals("sku-1002", SpokenNormalizer.normalize("sku one oh oh two")); }
    @Test void nullAndBlankAreSafe() { assertEquals("", SpokenNormalizer.normalize(null)); assertEquals("", SpokenNormalizer.normalize("   ")); }
    @Test void plainTypedTextIsUntouched() { assertEquals("show low stock for category dairy", SpokenNormalizer.normalize("Show low stock for category Dairy")); }
}
