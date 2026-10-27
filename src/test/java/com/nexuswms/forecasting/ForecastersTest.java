package com.nexuswms.forecasting;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ForecastersTest {
    @Test void movingAverageUsesLastWindow() {
        assertEquals(4.0, new MovingAverageForecaster().forecast(new double[]{1, 2, 3, 4, 5}, 3), 1e-9);
    }
    @Test void movingAverageShortHistoryAndEmpty() {
        assertEquals(1.5, new MovingAverageForecaster().forecast(new double[]{1, 2}, 10), 1e-9);
        assertEquals(0, new MovingAverageForecaster().forecast(new double[]{}, 3), 1e-9);
    }
    @Test void smoothingConstantSeriesIsConstant() {
        assertEquals(5.0, new ExponentialSmoothingForecaster().forecast(new double[]{5, 5, 5, 5}, 0.3), 1e-9);
    }
    @Test void smoothingWeightsRecentValues() {
        double f = new ExponentialSmoothingForecaster().forecast(new double[]{0, 0, 0, 10}, 0.5);
        assertEquals(5.0, f, 1e-9);
    }
    @Test void smoothingRejectsBadAlpha() {
        assertThrows(IllegalArgumentException.class, () -> new ExponentialSmoothingForecaster().forecast(new double[]{1, 2}, 0));
    }
    @Test void zScoreMatchesKnownValues() {
        assertEquals(1.645, SafetyStockCalculator.zScore(0.95), 0.01);
        assertEquals(2.326, SafetyStockCalculator.zScore(0.99), 0.01);
        assertEquals(0.0, SafetyStockCalculator.zScore(0.5), 0.01);
    }
    @Test void safetyStockScalesWithLeadTime() {
        int s4 = SafetyStockCalculator.safetyStock(10, 4, 0.95);
        int s16 = SafetyStockCalculator.safetyStock(10, 16, 0.95);
        assertEquals(33, s4);
        assertTrue(s16 > s4);
        assertEquals(0, SafetyStockCalculator.safetyStock(0, 5, 0.95));
    }
    @Test void normalCdfIsSane() {
        assertEquals(0.5, SafetyStockCalculator.normalCdf(0), 1e-6);
        assertEquals(0.975, SafetyStockCalculator.normalCdf(1.96), 0.002);
    }
}
