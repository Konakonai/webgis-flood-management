package com.floodgis.controller;

import com.floodgis.dto.PublicReportRequest;
import com.floodgis.dto.Result;
import com.floodgis.entity.WorkOrder;
import com.floodgis.entity.WorkOrderAttachment;
import com.floodgis.service.WorkOrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Validated
public class PublicReportController {
    private final WorkOrderService workOrderService;

    @PostMapping
    public Result<Map<String, Object>> create(@Valid @RequestBody PublicReportRequest request) {
        WorkOrder order = workOrderService.createPublicReport(request);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", order.getTrackingCode());
        response.put("trackingCode", order.getTrackingCode());
        response.put("status", order.getStatus());
        response.put("createdAt", order.getCreatedAt());
        return Result.success("上报成功", response);
    }

    @GetMapping("/track/{trackingCode}")
    public Result<Map<String, Object>> track(@PathVariable String trackingCode) {
        return Result.success(workOrderService.track(trackingCode.toUpperCase()));
    }

    @PostMapping("/{trackingCode}/images")
    public Result<Map<String, Object>> uploadImage(@PathVariable String trackingCode,
                                                   @RequestParam("file") MultipartFile file) {
        WorkOrderAttachment attachment = workOrderService.addImage(trackingCode.toUpperCase(), file);
        return Result.success("图片上传成功", Map.of(
                "id", attachment.getId(),
                "url", "/uploads/" + attachment.getRelativePath()));
    }

    /** Nearby unresolved reports as GeoJSON for avoidance analysis. */
    @GetMapping("/nearby")
    public Map<String, Object> nearby(
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double lng,
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
            @RequestParam(defaultValue = "1000") @Min(50) @Max(10000) int radiusMeters) {
        List<Map<String, Object>> features = workOrderService.nearby(lng, lat, radiusMeters).stream()
                .map(this::feature)
                .toList();
        return Map.of("type", "FeatureCollection", "features", features);
    }

    private Map<String, Object> feature(WorkOrder order) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", order.getTrackingCode());
        properties.put("depth", order.getWaterDepthCm());
        properties.put("description", order.getDescription());
        properties.put("status", order.getStatus());
        properties.put("createdAt", order.getCreatedAt());
        return Map.of(
                "type", "Feature",
                "geometry", Map.of("type", "Point", "coordinates", List.of(order.getLng(), order.getLat())),
                "properties", properties);
    }
}
