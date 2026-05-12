package cn.iocoder.yudao.detection.flink.util;

/**
 * Acoustic Emission (AE) feature calculator — pure Java, zero external dependencies.
 *
 * Computes 9 standard AE parameters from a voltage waveform segment:
 *   amplitude, energy, area, skewness, rise_time, duration, counts, RA, AF
 *
 * Thread-safe: all methods are static and side-effect-free.
 */
public final class AeFeatureCalculator {

    private AeFeatureCalculator() {}

    public static final int DEFAULT_SAMPLING_RATE = 2_000_000;
    public static final double DEFAULT_THRESHOLD_RATIO = 0.25;

    public static AeFeatures compute(double[] voltages, int samplingRate) {
        return compute(voltages, samplingRate, DEFAULT_THRESHOLD_RATIO);
    }

    public static AeFeatures compute(double[] voltages, int samplingRate, double thresholdRatio) {
        int n = voltages.length;
        if (n == 0) return AeFeatures.EMPTY;

        double dt = 1.0 / samplingRate;

        double maxAbs = 0;
        int peakIndex = 0;
        double sum = 0, sumSq = 0, sumAbs = 0;

        for (int i = 0; i < n; i++) {
            double v = voltages[i];
            double absV = Math.abs(v);
            if (absV > maxAbs) {
                maxAbs = absV;
                peakIndex = i;
            }
            sum += v;
            sumSq += v * v;
            sumAbs += absV;
        }

        double amplitude = maxAbs;
        double energy = sumSq;
        double area = sumAbs * dt;

        double mean = sum / n;
        double variance = sumSq / n - mean * mean;
        double sigma = Math.sqrt(Math.max(variance, 0));

        double skewness = 0;
        if (sigma > 1e-15) {
            double sumCubed = 0;
            for (int i = 0; i < n; i++) {
                double d = voltages[i] - mean;
                sumCubed += d * d * d;
            }
            skewness = (sumCubed / n) / (sigma * sigma * sigma);
        }

        double threshold = amplitude * thresholdRatio;

        int firstAbove = -1;
        int lastAbove = -1;
        int counts = 0;
        boolean prevAbove = false;

        for (int i = 0; i < n; i++) {
            boolean above = Math.abs(voltages[i]) >= threshold;
            if (above) {
                if (firstAbove < 0) firstAbove = i;
                lastAbove = i;
                if (!prevAbove) counts++;
            }
            prevAbove = above;
        }

        double riseTime;
        if (firstAbove >= 0 && peakIndex >= firstAbove) {
            riseTime = (peakIndex - firstAbove) * dt;
        } else {
            riseTime = 0;
        }

        double duration;
        if (firstAbove >= 0 && lastAbove >= firstAbove) {
            duration = (lastAbove - firstAbove) * dt;
        } else {
            duration = 0;
        }

        double ra = (amplitude > 1e-15) ? riseTime / amplitude : 0;
        double af = (duration > 1e-15) ? counts / duration : 0;

        return new AeFeatures(amplitude, energy, area, skewness,
                riseTime, duration, counts, ra, af);
    }

    public static class AeFeatures {
        public static final AeFeatures EMPTY = new AeFeatures(0, 0, 0, 0, 0, 0, 0, 0, 0);

        public final double amplitude;
        public final double energy;
        public final double area;
        public final double skewness;
        public final double riseTime;
        public final double duration;
        public final int counts;
        public final double ra;
        public final double af;

        public AeFeatures(double amplitude, double energy, double area, double skewness,
                          double riseTime, double duration, int counts, double ra, double af) {
            this.amplitude = amplitude;
            this.energy = energy;
            this.area = area;
            this.skewness = skewness;
            this.riseTime = riseTime;
            this.duration = duration;
            this.counts = counts;
            this.ra = ra;
            this.af = af;
        }
    }
}
