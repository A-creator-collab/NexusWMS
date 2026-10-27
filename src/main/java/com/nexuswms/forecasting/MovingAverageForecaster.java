package com.nexuswms.forecasting;

/** Simple moving average over the last {@code window} observations (all of them if fewer). */
public class MovingAverageForecaster {
    public double forecast(double[] history, int window) {
        if (history == null || history.length == 0 || window <= 0) return 0;
        int n = Math.min(window, history.length);
        double sum = 0;
        for (int i = history.length - n; i < history.length; i++) sum += history[i];
        return sum / n;
    }
}
