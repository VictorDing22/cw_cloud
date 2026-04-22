package cn.iocoder.yudao.module.filter.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 报警级别枚举
 *
 * @author yudao
 */
@Getter
@AllArgsConstructor
public enum AlertLevel {

    INFO("信息", "#2196F3", 1),
    WARNING("警告", "#FF9800", 2),
    ERROR("错误", "#F44336", 3),
    CRITICAL("严重", "#9C27B0", 4);

    /**
     * 级别名称
     */
    private final String name;

    /**
     * 显示颜色
     */
    private final String color;

    /**
     * 级别权重（用于比较）
     */
    private final Integer weight;
}


