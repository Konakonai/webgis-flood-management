package com.floodgis.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.floodgis.config.ApiException;
import com.floodgis.dto.Result;
import com.floodgis.entity.SysLog;
import com.floodgis.service.SysLogService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@Validated
public class SysLogController {

    private final SysLogService logService;

    /**
     * 分页查询操作日志
     */
    @GetMapping
    public Result<Page<SysLog>> list(
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw ApiException.badRequest("开始时间不能晚于结束时间");
        }

        LambdaQueryWrapper<SysLog> wrapper = new LambdaQueryWrapper<>();
        if (username != null && !username.isEmpty()) {
            wrapper.like(SysLog::getUsername, username);
        }
        if (action != null && !action.isEmpty()) {
            wrapper.eq(SysLog::getAction, action);
        }
        if (startTime != null) {
            wrapper.ge(SysLog::getCreatedAt, startTime);
        }
        if (endTime != null) {
            wrapper.le(SysLog::getCreatedAt, endTime);
        }
        wrapper.orderByDesc(SysLog::getCreatedAt);

        Page<SysLog> result = logService.page(new Page<>(page, size), wrapper);
        return Result.success(result);
    }

    /**
     * 获取日志详情
     */
    @GetMapping("/{id}")
    public Result<SysLog> getById(@PathVariable Long id) {
        SysLog log = logService.getById(id);
        if (log == null) {
            throw ApiException.notFound("日志不存在");
        }
        return Result.success(log);
    }
}
