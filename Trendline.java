/** Least-squares trendlines for charts: linear, exponential and a simple moving average. */
public final class Trendline {

    public final ChartSpec.Trend kind;
    /** y = a*x + b (linear) or y = a*e^(b*x) (exponential). */
    public final double a, b;
    /** Goodness of fit, 0..1. */
    public final double r2;

    private Trendline(ChartSpec.Trend kind, double a, double b, double r2) {
        this.kind = kind;
        this.a = a;
        this.b = b;
        this.r2 = r2;
    }

    /** Fits the points that have a number for both x and y; null if there are too few or they cannot be fitted. */
    public static Trendline fit(ChartSpec.Trend kind, double[] x, double[] y) {
        if (kind != ChartSpec.Trend.LINEAR && kind != ChartSpec.Trend.EXPONENTIAL) return null;
        boolean exp = kind == ChartSpec.Trend.EXPONENTIAL;
        int n = 0;
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int i = 0; i < Math.min(x.length, y.length); i++) {
            if (Double.isNaN(x[i]) || Double.isNaN(y[i])) continue;
            if (exp && y[i] <= 0) continue;
            double yy = exp ? Math.log(y[i]) : y[i];
            n++;
            sx += x[i];
            sy += yy;
            sxx += x[i] * x[i];
            sxy += x[i] * yy;
        }
        if (n < 2) return null;
        double den = n * sxx - sx * sx;
        if (Math.abs(den) < 1e-12) return null;
        double slope = (n * sxy - sx * sy) / den;
        double icpt = (sy - slope * sx) / n;
        double mean = sy / n;
        double ssTot = 0, ssRes = 0;
        for (int i = 0; i < Math.min(x.length, y.length); i++) {
            if (Double.isNaN(x[i]) || Double.isNaN(y[i]) || (exp && y[i] <= 0)) continue;
            double yy = exp ? Math.log(y[i]) : y[i];
            double f = slope * x[i] + icpt;
            ssTot += (yy - mean) * (yy - mean);
            ssRes += (yy - f) * (yy - f);
        }
        double r2 = ssTot < 1e-12 ? 1 : Math.max(0, 1 - ssRes / ssTot);
        return exp ? new Trendline(kind, Math.exp(icpt), slope, r2) : new Trendline(kind, slope, icpt, r2);
    }

    public double at(double x) {
        return kind == ChartSpec.Trend.EXPONENTIAL ? a * Math.exp(b * x) : a * x + b;
    }

    /** "y = 2.5x + 3" or "y = 4.1e^0.2x". */
    public String equation() {
        if (kind == ChartSpec.Trend.EXPONENTIAL) return "y = " + sig(a) + "e^" + sig(b) + "x";
        String bs = b < 0 ? " - " + sig(-b) : " + " + sig(b);
        return "y = " + sig(a) + "x" + bs;
    }

    public String label() {
        return equation() + "   R² = " + String.format("%.3f", r2);
    }

    private static String sig(double v) {
        String s = String.format("%.4g", v);
        if (s.contains("e")) return s;
        if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }

    /** Average of the last {@code period} values at each position; NaN until there are enough numbers. */
    public static double[] movingAverage(double[] y, int period) {
        double[] out = new double[y.length];
        for (int i = 0; i < y.length; i++) {
            out[i] = Double.NaN;
            if (i < period - 1) continue;
            double sum = 0;
            boolean ok = true;
            for (int k = i - period + 1; k <= i; k++) {
                if (Double.isNaN(y[k])) {
                    ok = false;
                    break;
                }
                sum += y[k];
            }
            if (ok) out[i] = sum / period;
        }
        return out;
    }
}
