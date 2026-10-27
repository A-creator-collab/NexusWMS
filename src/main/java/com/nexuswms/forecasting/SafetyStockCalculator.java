package com.nexuswms.forecasting;

/** Service-level based safety stock: z * sigma_daily * sqrt(leadTimeDays). Default service level 95% (z ~ 1.645). */
public final class SafetyStockCalculator {
    public static final double DEFAULT_SERVICE_LEVEL = 0.95;
    private SafetyStockCalculator() {}

    /** Inverse standard-normal CDF (Abramowitz & Stegun 26.2.23, |error| < 4.5e-4). */
    public static double zScore(double p) {
        if (p <= 0 || p >= 1) throw new IllegalArgumentException("service level must be in (0,1)");
        if (p < 0.5) return -zScore(1 - p);
        double t = Math.sqrt(-2 * Math.log(1 - p));
        return t - (2.515517 + 0.802853 * t + 0.010328 * t * t) / (1 + 1.432788 * t + 0.189269 * t * t + 0.001308 * t * t * t);
    }

    public static int safetyStock(double dailyStdDev, double leadTimeDays, double serviceLevel) {
        if (dailyStdDev <= 0 || leadTimeDays <= 0) return 0;
        return (int) Math.ceil(zScore(serviceLevel) * dailyStdDev * Math.sqrt(leadTimeDays));
    }

    /** Standard normal CDF via erf approximation (A&S 7.1.26). */
    public static double normalCdf(double x) {
        double s = x < 0 ? -1 : 1, a = Math.abs(x) / Math.sqrt(2);
        double t = 1 / (1 + 0.3275911 * a);
        double y = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * Math.exp(-a * a);
        return 0.5 * (1 + s * y);
    }
}
