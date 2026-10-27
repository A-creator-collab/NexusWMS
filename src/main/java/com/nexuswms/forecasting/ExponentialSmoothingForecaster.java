package com.nexuswms.forecasting;

/** Simple exponential smoothing: level_t = alpha*x_t + (1-alpha)*level_{t-1}. Holt-Winters is not implemented in v1. */
public class ExponentialSmoothingForecaster {
    public double forecast(double[] history, double alpha) {
        if (history == null || history.length == 0) return 0;
        if (alpha <= 0 || alpha > 1) throw new IllegalArgumentException("alpha must be in (0,1]");
        double level = history[0];
        for (int i = 1; i < history.length; i++) level = alpha * history[i] + (1 - alpha) * level;
        return level;
    }
}
