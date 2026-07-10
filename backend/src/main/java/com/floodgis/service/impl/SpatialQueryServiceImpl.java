package com.floodgis.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.floodgis.dto.SpatialFacilityRecord;
import com.floodgis.dto.SpatialQueryRequest;
import com.floodgis.mapper.SpatialFacilityMapper;
import com.floodgis.service.SpatialQueryService;
import com.floodgis.service.support.GeoJsonValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SpatialQueryServiceImpl implements SpatialQueryService {

    private static final int MAX_NAME_LENGTH = 100;
    private static final Set<String> ALLOWED_TYPES = Set.of("all", "waterlogging", "pump");

    private final SpatialFacilityMapper spatialFacilityMapper;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, Object> query(SpatialQueryRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }

        String name = normalizeName(request.getName());
        String type = normalizeType(request.getType());
        String bufferGeoJson = normalizeBuffer(request.getBufferGeoJSON());
        if (bufferGeoJson != null
                && !Boolean.TRUE.equals(spatialFacilityMapper.isValidBuffer(bufferGeoJson))) {
            throw new IllegalArgumentException("bufferGeoJSON 的多边形拓扑无效");
        }

        List<SpatialFacilityRecord> records = spatialFacilityMapper.search(name, type, bufferGeoJson);
        List<Map<String, Object>> features = new ArrayList<>(records.size());
        for (SpatialFacilityRecord record : records) {
            features.add(toFeature(record));
        }

        Map<String, Object> collection = new LinkedHashMap<>();
        collection.put("type", "FeatureCollection");
        collection.put("features", features);
        return collection;
    }

    private String normalizeName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("设施名称查询条件不能超过 " + MAX_NAME_LENGTH + " 个字符");
        }
        return name;
    }

    private String normalizeType(String value) {
        String type = value == null || value.isBlank() ? "all" : value.trim();
        if (!ALLOWED_TYPES.contains(type)) {
            throw new IllegalArgumentException("type 只能是 all、waterlogging 或 pump");
        }
        return type;
    }

    private String normalizeBuffer(JsonNode buffer) {
        if (buffer == null || buffer.isNull()) {
            return null;
        }
        GeoJsonValidator.validatePolygonOrMultiPolygon(buffer);
        return buffer.toString();
    }

    private Map<String, Object> toFeature(SpatialFacilityRecord record) {
        JsonNode geometry;
        try {
            geometry = objectMapper.readTree(record.getGeomJson());
            GeoJsonValidator.validatePoint(geometry);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new IllegalStateException("数据库中的空间设施几何数据无效", e);
        }

        ObjectNode properties = objectMapper.createObjectNode();
        putNullable(properties, "id", record.getId());
        putNullable(properties, "name", record.getName());
        putNullable(properties, "type", record.getFacilityType());
        putNullable(properties, "typeName", record.getTypeName());
        putNullable(properties, "status", record.getStatus());
        putNullable(properties, "statusName", record.getStatusName());
        putNullable(properties, "manager", record.getManager());
        putNullable(properties, "phone", record.getPhone());
        putNullable(properties, "address", record.getAddress());
        putNullable(properties, "waterDepth", record.getWaterDepth());
        putNullable(properties, "capacity", record.getCapacity());

        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "Feature");
        feature.put("properties", properties);
        feature.put("geometry", geometry);
        return feature;
    }

    private void putNullable(ObjectNode object, String field, String value) {
        if (value == null) {
            object.putNull(field);
        } else {
            object.put(field, value);
        }
    }
}
