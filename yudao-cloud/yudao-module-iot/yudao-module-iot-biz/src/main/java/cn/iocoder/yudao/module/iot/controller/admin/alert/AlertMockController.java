package cn.iocoder.yudao.module.iot.controller.admin.alert;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 告警管理 Mock Controller
 * 提供模拟数据，快速让前端正常工作
 * 后续可以逐步替换为真实实现
 *
 * @author 芋道源码
 */
@Tag(name = "管理后台 - 告警管理Mock")
@RestController
@RequestMapping("/iot/alert")
@Slf4j
public class AlertMockController {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ==================== 告警用户相关接口 ====================

    @GetMapping("/user/list")
    @Operation(summary = "获取告警用户列表")
    public CommonResult<PageResult<Map<String, Object>>> getAlertUserList(
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String contactName,
            @RequestParam(required = false) Long gatewayId) {
        
        log.info("获取告警用户列表: pageNo={}, pageSize={}, contactName={}, gatewayId={}", 
                pageNo, pageSize, contactName, gatewayId);
        
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> user1 = new HashMap<>();
        user1.put("id", 1L);
        user1.put("contactName", "张三");
        user1.put("contactType", "email");
        user1.put("gatewayId", 1L);
        user1.put("gatewayName", "消化炉关键（广州）");
        user1.put("gatewayLocation", "研究公司");
        user1.put("language", "zh-CN");
        user1.put("phone", "13800138000");
        user1.put("email", "zhangsan@example.com");
        user1.put("receiverCount", 10);
        user1.put("status", 1);
        user1.put("createTime", LocalDateTime.now().format(formatter));
        list.add(user1);
        
        PageResult<Map<String, Object>> pageResult = new PageResult<>();
        pageResult.setList(list);
        pageResult.setTotal(1L);
        
        return CommonResult.success(pageResult);
    }

    @GetMapping("/user/{id}")
    @Operation(summary = "获取告警用户详情")
    public CommonResult<Map<String, Object>> getAlertUser(@PathVariable Long id) {
        log.info("获取告警用户详情: id={}", id);
        
        Map<String, Object> user = new HashMap<>();
        user.put("id", id);
        user.put("contactName", "张三");
        user.put("gatewayId", 1L);
        user.put("language", "zh-CN");
        user.put("phone", "13800138000");
        user.put("email", "zhangsan@example.com");
        user.put("receiverCount", 10);
        
        return CommonResult.success(user);
    }

    @PostMapping("/user/create")
    @Operation(summary = "创建告警用户")
    public CommonResult<Long> createAlertUser(@RequestBody Map<String, Object> data) {
        log.info("创建告警用户: data={}", data);
        return CommonResult.success(System.currentTimeMillis());
    }

    @PutMapping("/user/update")
    @Operation(summary = "更新告警用户")
    public CommonResult<Boolean> updateAlertUser(@RequestBody Map<String, Object> data) {
        log.info("更新告警用户: data={}", data);
        return CommonResult.success(true);
    }

    @DeleteMapping("/user/delete")
    @Operation(summary = "删除告警用户")
    public CommonResult<Boolean> deleteAlertUser(@RequestBody Map<String, Object> data) {
        log.info("删除告警用户: data={}", data);
        return CommonResult.success(true);
    }

    @PutMapping("/user/update-status")
    @Operation(summary = "更新告警用户状态")
    public CommonResult<Boolean> updateAlertUserStatus(@RequestBody Map<String, Object> data) {
        log.info("更新告警用户状态: data={}", data);
        return CommonResult.success(true);
    }

    // ==================== 告警场景相关接口 ====================

    @GetMapping("/scene/list")
    @Operation(summary = "获取告警场景列表")
    public CommonResult<PageResult<Map<String, Object>>> getAlertSceneList(
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize) {
        
        log.info("获取告警场景列表: pageNo={}, pageSize={}", pageNo, pageSize);
        
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> scene1 = new HashMap<>();
        scene1.put("id", 1L);
        scene1.put("sceneName", "10级超压");
        scene1.put("sceneType", "intensity");
        scene1.put("gatewayId", 1L);
        scene1.put("gatewayName", "消化炉关键（广州）");
        scene1.put("gatewayLocation", "研究公司");
        scene1.put("alertLevel", 2);
        scene1.put("triggerDuration", 20);
        scene1.put("status", 1);
        scene1.put("notifyMethod", Arrays.asList("email"));
        scene1.put("createTime", LocalDateTime.now().format(formatter));
        list.add(scene1);
        
        PageResult<Map<String, Object>> pageResult = new PageResult<>();
        pageResult.setList(list);
        pageResult.setTotal(1L);
        
        return CommonResult.success(pageResult);
    }

    @GetMapping("/scene/{id}")
    @Operation(summary = "获取告警场景详情")
    public CommonResult<Map<String, Object>> getAlertScene(@PathVariable Long id) {
        log.info("获取告警场景详情: id={}", id);
        
        Map<String, Object> scene = new HashMap<>();
        scene.put("id", id);
        scene.put("sceneName", "10级超压");
        scene.put("sceneType", "intensity");
        scene.put("gatewayId", 1L);
        scene.put("alertLevel", 2);
        scene.put("triggerDuration", 20);
        scene.put("status", 1);
        scene.put("ratingType", "auto");
        scene.put("evaluationRule", "any");
        scene.put("statisticsDuration", 20);
        scene.put("thresholdType", "unlimited");
        scene.put("bmwThreshold", 1);
        scene.put("notifyMethod", Arrays.asList("email"));
        scene.put("notifyUsers", Arrays.asList(1L));
        
        return CommonResult.success(scene);
    }

    @PostMapping("/scene/create")
    @Operation(summary = "创建告警场景")
    public CommonResult<Long> createAlertScene(@RequestBody Map<String, Object> data) {
        log.info("创建告警场景: data={}", data);
        return CommonResult.success(System.currentTimeMillis());
    }

    @PutMapping("/scene/update")
    @Operation(summary = "更新告警场景")
    public CommonResult<Boolean> updateAlertScene(@RequestBody Map<String, Object> data) {
        log.info("更新告警场景: data={}", data);
        return CommonResult.success(true);
    }

    @DeleteMapping("/scene/delete")
    @Operation(summary = "删除告警场景")
    public CommonResult<Boolean> deleteAlertScene(@RequestBody Map<String, Object> data) {
        log.info("删除告警场景: data={}", data);
        return CommonResult.success(true);
    }

    @PostMapping("/scene/copy")
    @Operation(summary = "复制告警场景")
    public CommonResult<Long> copyAlertScene(@RequestBody Map<String, Object> data) {
        log.info("复制告警场景: data={}", data);
        return CommonResult.success(System.currentTimeMillis());
    }

    // ==================== 用户消息相关接口 ====================

    @GetMapping("/message/list")
    @Operation(summary = "获取用户消息列表")
    public CommonResult<PageResult<Map<String, Object>>> getAlertMessageList(
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize) {
        
        log.info("获取用户消息列表: pageNo={}, pageSize={}", pageNo, pageSize);
        
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> message1 = new HashMap<>();
        message1.put("id", 1L);
        message1.put("messageType", "alert");
        message1.put("title", "告警通知");
        message1.put("content", "设备JF_RAEM1_WP1_03触发10级超压告警");
        message1.put("level", "high");
        message1.put("readStatus", 0);
        message1.put("deviceInfo", "JF_RAEM1_WP1_03");
        message1.put("createTime", LocalDateTime.now().format(formatter));
        list.add(message1);
        
        PageResult<Map<String, Object>> pageResult = new PageResult<>();
        pageResult.setList(list);
        pageResult.setTotal(1L);
        
        return CommonResult.success(pageResult);
    }

    @PutMapping("/message/mark-read")
    @Operation(summary = "标记消息已读")
    public CommonResult<Boolean> markMessageAsRead(@RequestBody Map<String, Object> data) {
        log.info("标记消息已读: data={}", data);
        return CommonResult.success(true);
    }

    @PutMapping("/message/mark-all-read")
    @Operation(summary = "全部标记已读")
    public CommonResult<Boolean> markAllMessagesAsRead() {
        log.info("全部标记已读");
        return CommonResult.success(true);
    }

    @DeleteMapping("/message/delete")
    @Operation(summary = "删除消息")
    public CommonResult<Boolean> deleteAlertMessage(@RequestBody Map<String, Object> data) {
        log.info("删除消息: data={}", data);
        return CommonResult.success(true);
    }

    // ==================== 告警日志相关接口 ====================

    @GetMapping("/log/list")
    @Operation(summary = "获取告警日志列表")
    public CommonResult<PageResult<Map<String, Object>>> getAlertLogList(
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize) {
        
        log.info("获取告警日志列表: pageNo={}, pageSize={}", pageNo, pageSize);
        
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> log1 = new HashMap<>();
        log1.put("id", 1L);
        log1.put("sceneId", 1L);
        log1.put("sceneName", "10级超压");
        log1.put("deviceKey", "JF_RAEM1_WP1_03");
        log1.put("deviceName", "JF_RAEM1_WP1_03");
        log1.put("alertLevel", 1);
        
        Map<String, Object> alertParams = new HashMap<>();
        alertParams.put("amplitude", 73.358);
        alertParams.put("energy", 74.073);
        alertParams.put("rms", 0.831);
        log1.put("alertParams", alertParams);
        
        log1.put("threshold", 40.0);
        log1.put("actualValue", 73.358);
        log1.put("handleStatus", 0);
        log1.put("createTime", LocalDateTime.now().format(formatter));
        list.add(log1);
        
        PageResult<Map<String, Object>> pageResult = new PageResult<>();
        pageResult.setList(list);
        pageResult.setTotal(1L);
        
        return CommonResult.success(pageResult);
    }

    @GetMapping("/log/{id}")
    @Operation(summary = "获取告警日志详情")
    public CommonResult<Map<String, Object>> getAlertLog(@PathVariable Long id) {
        log.info("获取告警日志详情: id={}", id);
        
        Map<String, Object> log = new HashMap<>();
        log.put("id", id);
        log.put("sceneId", 1L);
        log.put("sceneName", "10级超压");
        log.put("deviceKey", "JF_RAEM1_WP1_03");
        log.put("deviceName", "JF_RAEM1_WP1_03");
        log.put("alertLevel", 1);
        
        Map<String, Object> alertParams = new HashMap<>();
        alertParams.put("amplitude", 73.358);
        alertParams.put("energy", 74.073);
        alertParams.put("rms", 0.831);
        log.put("alertParams", alertParams);
        
        log.put("threshold", 40.0);
        log.put("actualValue", 73.358);
        log.put("handleStatus", 0);
        log.put("createTime", LocalDateTime.now().format(formatter));
        
        return CommonResult.success(log);
    }

    @PutMapping("/log/process")
    @Operation(summary = "处理告警")
    public CommonResult<Boolean> processAlertLog(@RequestBody Map<String, Object> data) {
        log.info("处理告警: data={}", data);
        return CommonResult.success(true);
    }

    @DeleteMapping("/log/delete")
    @Operation(summary = "删除告警日志")
    public CommonResult<Boolean> deleteAlertLog(@RequestBody Map<String, Object> data) {
        log.info("删除告警日志: data={}", data);
        return CommonResult.success(true);
    }

    @GetMapping("/log/export")
    @Operation(summary = "导出告警日志")
    public CommonResult<String> exportAlertLog() {
        log.info("导出告警日志");
        return CommonResult.success("export-success");
    }

    // ==================== 统计分析相关接口 ====================

    @GetMapping("/statistics")
    @Operation(summary = "获取告警统计")
    public CommonResult<Map<String, Object>> getAlertStatistics(
            @RequestParam(required = false) String timeRange,
            @RequestParam(required = false) String deviceKey) {
        
        log.info("获取告警统计: timeRange={}, deviceKey={}", timeRange, deviceKey);
        
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("total", 100);
        statistics.put("unhandled", 10);
        statistics.put("processing", 20);
        statistics.put("handled", 70);
        
        Map<String, Integer> levelDistribution = new HashMap<>();
        levelDistribution.put("level1", 20);
        levelDistribution.put("level2", 30);
        levelDistribution.put("level3", 25);
        levelDistribution.put("level4", 15);
        levelDistribution.put("level5", 10);
        statistics.put("levelDistribution", levelDistribution);
        
        return CommonResult.success(statistics);
    }

    @GetMapping("/trend")
    @Operation(summary = "获取告警趋势")
    public CommonResult<List<Map<String, Object>>> getAlertTrend(
            @RequestParam(required = false) String timeRange,
            @RequestParam(required = false) String type) {
        
        log.info("获取告警趋势: timeRange={}, type={}", timeRange, type);
        
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            Map<String, Object> data = new HashMap<>();
            data.put("time", LocalDateTime.now().minusDays(6 - i).format(formatter));
            data.put("count", (int) (Math.random() * 50));
            data.put("level1", (int) (Math.random() * 10));
            data.put("level2", (int) (Math.random() * 15));
            data.put("level3", (int) (Math.random() * 12));
            data.put("level4", (int) (Math.random() * 8));
            data.put("level5", (int) (Math.random() * 5));
            trend.add(data);
        }
        
        return CommonResult.success(trend);
    }
}

