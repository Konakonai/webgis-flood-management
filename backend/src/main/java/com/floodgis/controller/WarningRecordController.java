package com.floodgis.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.floodgis.aspect.LogOperation;
import com.floodgis.config.ApiException;
import com.floodgis.dto.Result;
import com.floodgis.entity.WarningRecord;
import com.floodgis.security.JwtUserDetails;
import com.floodgis.service.WarningRecordService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/warnings")
@RequiredArgsConstructor
@Validated
public class WarningRecordController {
    private final WarningRecordService warningRecordService;

    @GetMapping
    public Result<Page<WarningRecord>> list(
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer level,
            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<WarningRecord> wrapper = new LambdaQueryWrapper<>();
        if (hasText(status)) wrapper.eq(WarningRecord::getStatus, status);
        if (level != null) wrapper.eq(WarningRecord::getWarningLevel, level);
        if (hasText(keyword)) {
            String value = keyword.trim();
            wrapper.and(group -> group.like(WarningRecord::getTitle, value)
                    .or().like(WarningRecord::getContent, value));
        }
        wrapper.orderByDesc(WarningRecord::getCreatedAt);
        return Result.success(warningRecordService.page(new Page<>(page, size), wrapper));
    }

    @GetMapping("/{id}")
    public Result<WarningRecord> getById(@PathVariable Long id) {
        WarningRecord record = warningRecordService.getById(id);
        if (record == null) throw ApiException.notFound("预警记录不存在");
        return Result.success(record);
    }

    @PutMapping("/{id}/confirm")
    @LogOperation(action = "CONFIRM", module = "WARNING", description = "确认预警")
    public Result<WarningRecord> confirm(@PathVariable Long id,
                                         @RequestBody(required = false) Map<String, String> ignored,
                                         @AuthenticationPrincipal JwtUserDetails user) {
        return Result.success("确认成功", warningRecordService.confirm(id, actor(user)));
    }

    @PutMapping("/{id}/reject")
    @LogOperation(action = "REJECT", module = "WARNING", description = "驳回预警")
    public Result<WarningRecord> reject(@PathVariable Long id,
                                        @RequestBody(required = false) Map<String, String> ignored,
                                        @AuthenticationPrincipal JwtUserDetails user) {
        return Result.success("驳回成功", warningRecordService.reject(id, actor(user)));
    }

    @PutMapping("/{id}/publish")
    @LogOperation(action = "PUBLISH", module = "WARNING", description = "发布预警")
    public Result<WarningRecord> publish(@PathVariable Long id,
                                         @RequestBody WarningRecord updateInfo,
                                         @AuthenticationPrincipal JwtUserDetails user) {
        return Result.success("发布成功", warningRecordService.publish(id, updateInfo, actor(user)));
    }

    @PutMapping("/{id}/revoke")
    @LogOperation(action = "REVOKE", module = "WARNING", description = "撤销预警")
    public Result<WarningRecord> revoke(@PathVariable Long id,
                                        @RequestBody(required = false) Map<String, String> ignored,
                                        @AuthenticationPrincipal JwtUserDetails user) {
        return Result.success("撤销成功", warningRecordService.revoke(id, actor(user)));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(warningRecordService.statistics());
    }

    @GetMapping("/timeline")
    public Result<List<WarningRecord>> timeline(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(required = false) String status) {
        return Result.success(warningRecordService.history(start, end, status));
    }

    private String actor(JwtUserDetails user) {
        if (user == null) throw ApiException.unauthorized("缺少认证用户");
        return user.getUsername();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
