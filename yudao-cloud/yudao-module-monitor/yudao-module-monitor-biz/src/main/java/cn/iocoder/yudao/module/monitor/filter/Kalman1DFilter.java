package cn.iocoder.yudao.module.monitor.filter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 标准线性一维卡尔曼滤波（随机游走模型）。
 *
 * <pre>
 * 预测：x = x
 *      P = P + Q
 * 更新：K = P / (P + R)
 *      x = x + K * (z - x)
 *      P = (1 - K) * P
 * </pre>
 *
 * <p>初始化：x0 取前 N 点均值（N>=1）。</p>
 */
public class Kalman1DFilter implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final double q;
    private final double r;
    private final double p0;
    private final int x0N;

    private boolean initialized;
    private double x;
    private double p;

    // 用于计算初始均值
    private int initCount;
    private double initSum;

    public Kalman1DFilter(double q, double r, double p0, int x0N) {
        this.q = q;
        this.r = r;
        this.p0 = p0;
        this.x0N = Math.max(1, x0N);
        this.initialized = false;
        this.x = 0.0;
        this.p = p0;
        this.initCount = 0;
        this.initSum = 0.0;
    }

    public double filter(double z) {
        if (!initialized) {
            initSum += z;
            initCount++;
            x = initSum / initCount;
            p = p0;
            if (initCount >= x0N) {
                initialized = true;
            }
            // 初始化阶段直接输出当前均值，避免前几帧抖动
            return x;
        }

        // predict
        p = p + q;
        // update
        double k = p / (p + r);
        x = x + k * (z - x);
        p = (1 - k) * p;
        return x;
    }
}

