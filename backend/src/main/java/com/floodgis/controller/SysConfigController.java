package com.floodgis.controller;

import com.floodgis.dto.Result;
import com.floodgis.aspect.LogOperation;
import com.floodgis.config.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.floodgis.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class SysConfigController {

    private final SysConfigService configService;
    private final ObjectMapper objectMapper;

    /**
     * 获取指定的配置值
     */
    @GetMapping("/{key}")
    public Result<Map<String, String>> getConfig(@PathVariable String key) {
        String value = configService.getConfigValue(key);
        if (value == null) {
            throw ApiException.notFound("配置项不存在: " + key);
        }
        Map<String, String> data = new HashMap<>();
        data.put("key", key);
        data.put("value", value);
        return Result.success(data);
    }

    /**
     * 更新配置值
     */
    @PutMapping("/{key}")
    @LogOperation(action = "UPDATE", module = "CONFIG", description = "更新系统配置")
    public Result<Void> updateConfig(@PathVariable String key, @RequestBody Map<String, String> body) {
        String value = body.get("value");
        if (value == null || value.isBlank()) throw ApiException.badRequest("配置值不能为空");
        validateThreshold(key, value);
        boolean updated = configService.updateConfigValue(key, value);
        if (updated) {
            return Result.success("配置更新成功", null);
        }
        throw ApiException.notFound("配置项不存在: " + key);
    }

    private void validateThreshold(String key, String value) {
        if (!key.endsWith("_warning") && !key.endsWith("_danger")) return;
        try {
            JsonNode number = objectMapper.readTree(value).get("value");
            if (number == null || !number.isNumber() || number.asDouble() < 0) {
                throw ApiException.badRequest("阈值配置必须包含非负数值字段 value");
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("阈值配置必须是有效 JSON");
        }
    }
}
