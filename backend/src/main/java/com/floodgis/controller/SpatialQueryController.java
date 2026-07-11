package com.floodgis.controller;

import com.floodgis.dto.SpatialQueryRequest;
import com.floodgis.service.SpatialQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/spatial-query")
@RequiredArgsConstructor
public class SpatialQueryController {

    private final SpatialQueryService spatialQueryService;

    /**
     * 直接返回 GeoJSON FeatureCollection，不使用全局 Result 包装。
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> query(@RequestBody SpatialQueryRequest request) {
        try {
            return ResponseEntity.ok(spatialQueryService.query(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        }
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("请求体必须是合法 JSON"));
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("error", message);
        return result;
    }
}
