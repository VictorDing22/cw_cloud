package cn.iocoder.yudao.detection.flink.filter;

import java.io.Serializable;

/**
 * 4th-order Butterworth bandpass filter implemented as cascaded 2nd-order biquad sections:
 *   highpass (remove low-freq noise) → lowpass (remove high-freq noise)
 *
 * For AE (acoustic emission) signals at 2 MHz sampling rate:
 *   - Default low cutoff  = 100 kHz  (removes mechanical vibration, 50/60Hz, etc.)
 *   - Default high cutoff = 900 kHz  (stays below Nyquist, removes electronic noise)
 *
 * Each biquad implements the standard Direct Form II Transposed difference equation:
 *   y[n] = b0*x[n] + b1*x[n-1] + b2*x[n-2] - a1*y[n-1] - a2*y[n-2]
 *
 * Coefficients are computed via bilinear transform from the analog Butterworth prototype
 * (Robert Bristow-Johnson's Audio EQ Cookbook formulas).
 */
public class ButterworthBandpass implements Serializable {
    private static final long serialVersionUID = 1L;

    private final double[] hpB = new double[3];
    private final double[] hpA = new double[3];
    private final double[] lpB = new double[3];
    private final double[] lpA = new double[3];

    // Filter state: [hpX1, hpX2, hpY1, hpY2, lpX1, lpX2, lpY1, lpY2]
    private final double[] state = new double[8];

    public ButterworthBandpass(double samplingRate, double lowCutoff, double highCutoff) {
        computeHighpass(samplingRate, lowCutoff);
        computeLowpass(samplingRate, highCutoff);
    }

    /**
     * Default for AE signals: fs=2MHz, low=100kHz, high=900kHz
     */
    public ButterworthBandpass() {
        this(2_000_000.0, 100_000.0, 900_000.0);
    }

    /**
     * Apply filter to one sample. Stateful — call sequentially for continuous signal.
     */
    public double apply(double x) {
        // Stage 1: 2nd-order Butterworth highpass
        double hpY = hpB[0] * x + hpB[1] * state[0] + hpB[2] * state[1]
                     - hpA[1] * state[2] - hpA[2] * state[3];
        state[1] = state[0]; // hpX2 = hpX1
        state[0] = x;        // hpX1 = x
        state[3] = state[2]; // hpY2 = hpY1
        state[2] = hpY;      // hpY1 = hpY

        // Stage 2: 2nd-order Butterworth lowpass
        double lpY = lpB[0] * hpY + lpB[1] * state[4] + lpB[2] * state[5]
                     - lpA[1] * state[6] - lpA[2] * state[7];
        state[5] = state[4]; // lpX2 = lpX1
        state[4] = hpY;      // lpX1 = hpY
        state[7] = state[6]; // lpY2 = lpY1
        state[6] = lpY;      // lpY1 = lpY

        return lpY;
    }

    /**
     * Get the filter state array (for Flink state serialization).
     */
    public double[] getState() {
        return state.clone();
    }

    /**
     * Restore filter state from a saved array.
     */
    public void restoreState(double[] saved) {
        if (saved != null && saved.length == 8) {
            System.arraycopy(saved, 0, state, 0, 8);
        }
    }

    /**
     * Reset filter state to zero (e.g., for a new signal segment).
     */
    public void reset() {
        java.util.Arrays.fill(state, 0.0);
    }

    // 2nd-order Butterworth highpass via bilinear transform
    private void computeHighpass(double fs, double fc) {
        double w0 = 2.0 * Math.PI * fc / fs;
        double cosW0 = Math.cos(w0);
        double sinW0 = Math.sin(w0);
        double alpha = sinW0 / Math.sqrt(2.0); // Q = 1/sqrt(2) for Butterworth

        double a0 = 1.0 + alpha;
        hpB[0] = (1.0 + cosW0) / 2.0 / a0;
        hpB[1] = -(1.0 + cosW0) / a0;
        hpB[2] = (1.0 + cosW0) / 2.0 / a0;
        hpA[0] = 1.0;
        hpA[1] = -2.0 * cosW0 / a0;
        hpA[2] = (1.0 - alpha) / a0;
    }

    // 2nd-order Butterworth lowpass via bilinear transform
    private void computeLowpass(double fs, double fc) {
        double w0 = 2.0 * Math.PI * fc / fs;
        double cosW0 = Math.cos(w0);
        double sinW0 = Math.sin(w0);
        double alpha = sinW0 / Math.sqrt(2.0);

        double a0 = 1.0 + alpha;
        lpB[0] = (1.0 - cosW0) / 2.0 / a0;
        lpB[1] = (1.0 - cosW0) / a0;
        lpB[2] = (1.0 - cosW0) / 2.0 / a0;
        lpA[0] = 1.0;
        lpA[1] = -2.0 * cosW0 / a0;
        lpA[2] = (1.0 - alpha) / a0;
    }
}
