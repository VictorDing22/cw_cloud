package cn.iocoder.yudao.framework.apilog.core.interceptor;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.framework.common.util.spring.SpringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StopWatch;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * API 访问日志 Interceptor
 *
 * 目的：在非 prod 环境时，打印 request 和 response 两条日志到日志文件（控制台）中。
 *
 * @author 芋道源码
 */
@Slf4j
public class ApiAccessLogInterceptor implements HandlerInterceptor {

    public static final String ATTRIBUTE_HANDLER_METHOD = "HANDLER_METHOD";

    private static final String ATTRIBUTE_STOP_WATCH = "ApiAccessLogInterceptor.StopWatch";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 记录 HandlerMethod，提供给 ApiAccessLogFilter 使用
        HandlerMethod handlerMethod = handler instanceof HandlerMethod ? (HandlerMethod) handler : null;
        if (handlerMethod != null) {
            request.setAttribute(ATTRIBUTE_HANDLER_METHOD, handlerMethod);
        }

        // 打印 request 日志
        if (!SpringUtils.isProd()) {
            // 调试：记录 /monitor/realtime/analyze 请求的 Content-Type
            String uri = request.getRequestURI();
            if (uri != null && uri.contains("/monitor/realtime/analyze")) {
                String contentType = request.getContentType();
                log.warn("🔍 [DEBUG] /monitor/realtime/analyze 请求详情:");
                log.warn("  - Method: {}", request.getMethod());
                log.warn("  - URI: {}", uri);
                log.warn("  - Content-Type: {}", contentType);
                log.warn("  - Content-Length: {}", request.getContentLength());
                log.warn("  - Handler 类型: {}", handler != null ? handler.getClass().getName() : "null");
                if (handlerMethod != null) {
                    log.warn("  - ✅ 匹配到 HandlerMethod: {}.{}", 
                            handlerMethod.getBeanType().getSimpleName(), 
                            handlerMethod.getMethod().getName());
                } else {
                    log.warn("  - ⚠️ Handler 不是 HandlerMethod: {}", handler != null ? handler.getClass().getName() : "null");
                }
                if (contentType != null && contentType.contains("multipart")) {
                    log.warn("  ✅ 检测到 multipart/form-data 请求");
                } else {
                    log.warn("  ⚠️ 不是 multipart/form-data 请求！可能是问题所在");
                }
            }
            
            Map<String, String> queryString = ServletUtils.getParamMap(request);
            String requestBody = ServletUtils.isJsonRequest(request) ? ServletUtils.getBody(request) : null;
            if (CollUtil.isEmpty(queryString) && StrUtil.isEmpty(requestBody)) {
                log.info("[preHandle][开始请求 URL({}) 无参数]", request.getRequestURI());
            } else {
                log.info("[preHandle][开始请求 URL({}) 参数({})]", request.getRequestURI(),
                        StrUtil.blankToDefault(requestBody, queryString.toString()));
            }
            // 计时
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            request.setAttribute(ATTRIBUTE_STOP_WATCH, stopWatch);
            // 打印 Controller 路径
            printHandlerMethodPosition(handlerMethod);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 打印 response 日志
        if (!SpringUtils.isProd()) {
            StopWatch stopWatch = (StopWatch) request.getAttribute(ATTRIBUTE_STOP_WATCH);
            if (stopWatch != null) {
                stopWatch.stop();
                log.info("[afterCompletion][完成请求 URL({}) 耗时({} ms)]",
                        request.getRequestURI(), stopWatch.getTotalTimeMillis());
            }
            
            // 记录异常（如果有）
            if (ex != null) {
                log.error("[afterCompletion][请求 URL({}) 发生异常]", request.getRequestURI(), ex);
            }
            
            // 调试：记录 handler 信息
            String uri = request.getRequestURI();
            if (uri != null && uri.contains("/monitor/realtime/analyze")) {
                log.warn("🔍 [DEBUG] Handler 信息:");
                log.warn("  - Handler 类型: {}", handler != null ? handler.getClass().getName() : "null");
                if (handler instanceof HandlerMethod) {
                    HandlerMethod handlerMethod = (HandlerMethod) handler;
                    log.warn("  - Controller: {}", handlerMethod.getBeanType().getName());
                    log.warn("  - Method: {}", handlerMethod.getMethod().getName());
                } else {
                    log.warn("  - ⚠️ Handler 不是 HandlerMethod，可能是 ResourceHttpRequestHandler 或其他");
                }
                if (ex != null) {
                    log.warn("  - ❌ 异常类型: {}", ex.getClass().getName());
                    log.warn("  - ❌ 异常消息: {}", ex.getMessage());
                }
            }
        }
    }

    /**
     * 打印 Controller 方法路径
     */
    private void printHandlerMethodPosition(HandlerMethod handlerMethod) {
        if (handlerMethod == null) {
            return;
        }
        Method method = handlerMethod.getMethod();
        Class<?> clazz = method.getDeclaringClass();
        try {
            // 获取 method 的 lineNumber
            List<String> clazzContents = FileUtil.readUtf8Lines(
                    ResourceUtil.getResource(null, clazz).getPath().replace("/target/classes/", "/src/main/java/")
                            + clazz.getSimpleName() + ".java");
            Optional<Integer> lineNumber = IntStream.range(0, clazzContents.size())
                    .filter(i -> clazzContents.get(i).contains(" " + method.getName() + "(")) // 简单匹配，不考虑方法重名
                    .mapToObj(i -> i + 1) // 行号从 1 开始
                    .findFirst();
            if (!lineNumber.isPresent()) {
                return;
            }
            // 打印结果
            System.out.printf("\tController 方法路径：%s(%s.java:%d)\n", clazz.getName(), clazz.getSimpleName(), lineNumber.get());
        } catch (Exception ignore) {
            // 忽略异常。原因：仅仅打印，非重要逻辑
        }
    }

}
