package com.floodgis.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.floodgis.config.ApiException;
import com.floodgis.dto.Result;
import com.floodgis.entity.GeoLayer;
import com.floodgis.service.GeoLayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/layers")
@RequiredArgsConstructor
public class GeoLayerController {

    private final GeoLayerService layerService;

    /**
     * 上传空间图层文件
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<GeoLayer> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description) {

        try {
            GeoLayer layer = layerService.uploadLayer(name, description, file);
            return Result.success("上传成功", layer);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(e.getMessage());
        } catch (IOException e) {
            throw new IllegalStateException("读取 GeoJSON 文件失败", e);
        }
    }

    /**
     * 可选的原始 GeoJSON 上传方式：名称和描述放在 query string，body 为 FeatureCollection。
     */
    @PostMapping(value = "/upload", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<GeoLayer> uploadJson(
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestBody JsonNode geoJson) {
        try {
            return Result.success("上传成功", layerService.uploadGeoJson(name, description, geoJson));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(e.getMessage());
        }
    }

    /**
     * 查询图层要素
     */
    @GetMapping("/{id}/features")
    public Result<Map<String, Object>> getFeatures(@PathVariable Long id) {
        return Result.success(layerService.getLayerFeatures(id));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleUnreadableBody(HttpMessageNotReadableException e) {
        return Result.badRequest("请求体必须是合法 JSON");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingParameter(MissingServletRequestParameterException e) {
        return Result.badRequest("缺少请求参数: " + e.getParameterName());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingPart(MissingServletRequestPartException e) {
        return Result.badRequest("缺少 multipart 字段: " + e.getRequestPartName());
    }
}
