package com.floodgis.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.floodgis.config.ApiException;
import com.floodgis.entity.GeoFeature;
import com.floodgis.entity.GeoLayer;
import com.floodgis.mapper.GeoFeatureMapper;
import com.floodgis.mapper.GeoLayerMapper;
import com.floodgis.service.GeoLayerService;
import com.floodgis.service.support.GeoJsonValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GeoLayerServiceImpl
        extends ServiceImpl<GeoLayerMapper, GeoLayer>
        implements GeoLayerService {

    static final int MAX_FEATURES = 10_000;
    static final long MAX_GEOJSON_BYTES = 5L * 1024 * 1024;

    private final GeoLayerMapper geoLayerMapper;
    private final GeoFeatureMapper geoFeatureMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public GeoLayer uploadLayer(String name, String description, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (file.getSize() > MAX_GEOJSON_BYTES) {
            throw new IllegalArgumentException("GeoJSON 文件不能超过 5 MB");
        }

        byte[] content = file.getBytes();
        if (content.length > MAX_GEOJSON_BYTES) {
            throw new IllegalArgumentException("GeoJSON 文件不能超过 5 MB");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(content);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("文件不是合法 JSON", e);
        }
        return persistLayer(name, description, root, file.getOriginalFilename(), (long) content.length);
    }

    @Override
    @Transactional
    public GeoLayer uploadGeoJson(String name, String description, JsonNode geoJson) {
        long fileSize = geoJson == null
                ? 0
                : geoJson.toString().getBytes(StandardCharsets.UTF_8).length;
        if (fileSize > MAX_GEOJSON_BYTES) {
            throw new IllegalArgumentException("GeoJSON 内容不能超过 5 MB");
        }
        return persistLayer(name, description, geoJson, null, fileSize);
    }

    private GeoLayer persistLayer(
            String name,
            String description,
            JsonNode root,
            String fileName,
            long fileSize) {

        String normalizedName = validateLayerMetadata(name, fileName);
        List<ParsedFeature> parsedFeatures = parseFeatureCollection(root);
        validateTopology(parsedFeatures);
        String layerType = determineLayerType(parsedFeatures);
        LocalDateTime now = LocalDateTime.now();

        GeoLayer layer = new GeoLayer();
        layer.setName(normalizedName);
        layer.setDescription(description);
        layer.setLayerType(layerType);
        layer.setCoordSystem("EPSG:4326");
        layer.setFeatureCount(parsedFeatures.size());
        layer.setFileName(fileName);
        layer.setFileSize(fileSize);
        layer.setCreatedAt(now);
        layer.setUpdatedAt(now);

        if (geoLayerMapper.insert(layer) != 1 || layer.getId() == null) {
            throw new IllegalStateException("图层元数据写入失败");
        }

        for (ParsedFeature parsed : parsedFeatures) {
            GeoFeature feature = new GeoFeature();
            feature.setLayerId(layer.getId());
            feature.setName(parsed.name());
            feature.setProperties(parsed.propertiesJson());
            feature.setGeomJson(parsed.geometryJson());
            feature.setCreatedAt(now);
            if (geoFeatureMapper.insertFeature(feature) != 1) {
                throw new IllegalStateException("图层要素写入失败");
            }
        }

        return layer;
    }

    private void validateTopology(List<ParsedFeature> parsedFeatures) {
        if (parsedFeatures.isEmpty()) {
            return;
        }
        List<String> geometries = parsedFeatures.stream()
                .map(ParsedFeature::geometryJson)
                .toList();
        if (!Boolean.TRUE.equals(geoFeatureMapper.areGeometriesValid(geometries))) {
            throw new IllegalArgumentException("GeoJSON 中存在拓扑无效的 geometry");
        }
    }

    private String validateLayerMetadata(String name, String fileName) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("图层名称不能为空");
        }
        String normalized = name.trim();
        if (normalized.length() > 100) {
            throw new IllegalArgumentException("图层名称不能超过 100 个字符");
        }
        if (fileName != null && fileName.length() > 200) {
            throw new IllegalArgumentException("文件名不能超过 200 个字符");
        }
        return normalized;
    }

    private List<ParsedFeature> parseFeatureCollection(JsonNode root) {
        if (root == null || root.isNull() || !root.isObject()) {
            throw new IllegalArgumentException("GeoJSON 根节点必须是对象");
        }
        JsonNode type = root.get("type");
        if (type == null || !type.isTextual() || !"FeatureCollection".equals(type.textValue())) {
            throw new IllegalArgumentException("GeoJSON.type 必须是 FeatureCollection");
        }
        JsonNode features = root.get("features");
        if (features == null || !features.isArray()) {
            throw new IllegalArgumentException("GeoJSON.features 必须是数组");
        }
        if (features.size() > MAX_FEATURES) {
            throw new IllegalArgumentException("GeoJSON 要素数不能超过 " + MAX_FEATURES);
        }

        List<ParsedFeature> result = new ArrayList<>(features.size());
        int index = 0;
        for (JsonNode feature : features) {
            result.add(parseFeature(feature, index));
            index++;
        }
        return result;
    }

    private ParsedFeature parseFeature(JsonNode feature, int index) {
        if (feature == null || !feature.isObject()) {
            throw new IllegalArgumentException("第 " + index + " 个要素必须是对象");
        }
        JsonNode type = feature.get("type");
        if (type == null || !type.isTextual() || !"Feature".equals(type.textValue())) {
            throw new IllegalArgumentException("第 " + index + " 个要素的 type 必须是 Feature");
        }

        JsonNode geometry = feature.get("geometry");
        try {
            GeoJsonValidator.validateGeometry(geometry);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("第 " + index + " 个要素的 geometry 无效: " + e.getMessage(), e);
        }

        JsonNode properties = feature.get("properties");
        if (properties != null && !properties.isNull() && !properties.isObject()) {
            throw new IllegalArgumentException("第 " + index + " 个要素的 properties 必须是对象或 null");
        }
        ObjectNode normalizedProperties = properties == null || properties.isNull()
                ? objectMapper.createObjectNode()
                : (ObjectNode) properties;

        String featureName = null;
        JsonNode nameNode = normalizedProperties.get("name");
        if (nameNode != null && !nameNode.isNull()) {
            if (!nameNode.isTextual()) {
                throw new IllegalArgumentException("第 " + index + " 个要素的 properties.name 必须是字符串");
            }
            featureName = nameNode.textValue().trim();
            if (featureName.length() > 200) {
                throw new IllegalArgumentException("第 " + index + " 个要素的名称不能超过 200 个字符");
            }
            if (featureName.isEmpty()) {
                featureName = null;
            }
        }

        return new ParsedFeature(
                featureName,
                geometry.toString(),
                normalizedProperties.toString(),
                GeoJsonValidator.geometryCategory(geometry));
    }

    private String determineLayerType(List<ParsedFeature> features) {
        if (features.isEmpty()) {
            return "UNKNOWN";
        }
        Set<String> categories = new LinkedHashSet<>();
        for (ParsedFeature feature : features) {
            categories.add(feature.category());
        }
        return categories.size() == 1 ? categories.iterator().next() : "MIXED";
    }

    @Override
    public Map<String, Object> getLayerFeatures(Long layerId) {
        GeoLayer layer = geoLayerMapper.selectById(layerId);
        if (layer == null) {
            throw ApiException.notFound("图层不存在");
        }

        List<GeoFeature> storedFeatures = geoFeatureMapper.findByLayerId(layerId);
        List<Map<String, Object>> features = new ArrayList<>(storedFeatures.size());
        for (GeoFeature stored : storedFeatures) {
            features.add(toGeoJsonFeature(stored));
        }

        Map<String, Object> collection = new LinkedHashMap<>();
        collection.put("type", "FeatureCollection");
        collection.put("features", features);
        return collection;
    }

    private Map<String, Object> toGeoJsonFeature(GeoFeature stored) {
        JsonNode geometry;
        ObjectNode properties;
        try {
            geometry = objectMapper.readTree(stored.getGeomJson());
            GeoJsonValidator.validateGeometry(geometry);
            JsonNode propertyNode = objectMapper.readTree(stored.getProperties());
            if (propertyNode == null || !propertyNode.isObject()) {
                throw new IllegalArgumentException("properties 不是 JSON 对象");
            }
            properties = (ObjectNode) propertyNode;
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new IllegalStateException("图层要素 " + stored.getId() + " 的 GeoJSON 数据无效", e);
        }

        if (stored.getName() != null && !properties.has("name")) {
            properties.put("name", stored.getName());
        }

        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "Feature");
        feature.put("id", stored.getId());
        feature.put("geometry", geometry);
        feature.put("properties", properties);
        return feature;
    }

    private record ParsedFeature(
            String name,
            String geometryJson,
            String propertiesJson,
            String category) {
    }
}
