package cn.iocoder.yudao.module.detection.logic.filter;

/**
 * Filter Strategy Interface.
 * Implementations should hold their own state.
 */
public interface FilterStrategy {
    
    /**
     * Process a single input value.
     * @param value The noisy input value
     * @param timestamp The timestamp of the value
     * @return The filtered (denoised) value
     */
    double filter(double value, long timestamp);

    /**
     * Get the name of the algorithm.
     */
    String getName();
}
