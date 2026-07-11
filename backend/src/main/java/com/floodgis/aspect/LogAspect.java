package com.floodgis.aspect;

import com.floodgis.entity.SysLog;
import com.floodgis.security.JwtUserDetails;
import com.floodgis.service.SysLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class LogAspect {

    private final SysLogService logService;

    @Around("@annotation(logAnnotation)")
    public Object around(ProceedingJoinPoint joinPoint, LogOperation logAnnotation) throws Throwable {
        long startTime = System.currentTimeMillis();

        // 获取请求信息
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;

        // 创建日志记录
        SysLog sysLog = new SysLog();
        sysLog.setAction(logAnnotation.action());
        sysLog.setModule(logAnnotation.module());
        sysLog.setDescription(logAnnotation.description());
        sysLog.setCreatedAt(LocalDateTime.now());

        if (request != null) {
            sysLog.setRequestUri(limit(request.getRequestURI(), 500));
            sysLog.setRequestMethod(request.getMethod());
            sysLog.setIpAddress(getClientIp(request));
        }

        // 获取当前用户
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof JwtUserDetails) {
            JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();
            sysLog.setUserId(userDetails.getUserId());
            sysLog.setUsername(userDetails.getUsername());
        }

        Object result;
        try {
            result = joinPoint.proceed();
            sysLog.setStatus("SUCCESS");
        } catch (Exception e) {
            sysLog.setStatus("FAIL");
            sysLog.setErrorMsg(e.getMessage());
            throw e;
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;
            sysLog.setExecutionTime(executionTime);
            try {
                logService.save(sysLog);
            } catch (Exception logError) {
                // Audit persistence must never hide or roll back the business result.
                log.error("审计日志写入失败: {}", logError.getMessage(), logError);
            }
        }

        return result;
    }

    /**
     * 获取客户端真实IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null) {
            ip = ip.split(",", 2)[0].trim();
        }
        if (ip == null || ip.isEmpty() || ip.length() > 45
                || !ip.matches("[0-9A-Fa-f:.]+")) {
            ip = request.getRemoteAddr();
        }
        if (ip == null || ip.isBlank() || ip.length() > 50) return "unknown";
        return ip;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }
}
