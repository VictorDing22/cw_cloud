package cn.iocoder.yudao.module.iot.dal.dataobject.alert;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

/**
 * 告警用户 DO
 *
 * @author 芋道源码
 */
@TableName("iot_alert_user")
@KeySequence("iot_alert_user_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertUserDO extends BaseDO {

    /**
     * 用户ID
     */
    @TableId
    private Long id;
    
    /**
     * 联系人姓名
     */
    private String contactName;
    
    /**
     * 联系类型(email,phone)
     */
    private String contactType;
    
    /**
     * 门户ID
     */
    private Long gatewayId;
    
    /**
     * 门户名称
     */
    private String gatewayName;
    
    /**
     * 门户位置
     */
    private String gatewayLocation;
    
    /**
     * 使用语言(zh-CN,en-US)
     */
    private String language;
    
    /**
     * 手机号
     */
    private String phone;
    
    /**
     * 邮箱
     */
    private String email;
    
    /**
     * 接收器数量(min)
     */
    private Integer receiverCount;
    
    /**
     * 状态(0-禁用 1-启用)
     */
    private Integer status;
    
    /**
     * 备注
     */
    private String remark;

}

