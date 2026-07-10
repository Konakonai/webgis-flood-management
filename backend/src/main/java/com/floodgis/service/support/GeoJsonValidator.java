package com.floodgis.service.support;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Iterator;

/**
 * 对即将交给 PostGIS 的 GeoJSON geometry 做结构校验。
 *
 * <p>这里只接受与数据库二维 geometry typmod 一致的 WGS84 二维坐标，
 * 并限制单个几何的坐标数，
 * 防止将未验证的任意 JSON 直接送入 ST_GeomFromGeoJSON。</p>
 */
public final class GeoJsonValidator {

    public static final int DEFAULT_MAX_POSITIONS = 100_000;
    private static final int MAX_GEOMETRY_COLLECTION_DEPTH = 8;

    private GeoJsonValidator() {
    }

    public static void validatePolygonOrMultiPolygon(JsonNode geometry) {
        requireGeometryObject(geometry);
        String type = requiredText(geometry, "type");
        if (!"Polygon".equals(type) && !"MultiPolygon".equals(type)) {
            throw new IllegalArgumentException("bufferGeoJSON.type 必须是 Polygon 或 MultiPolygon");
        }
        validateGeometry(geometry, DEFAULT_MAX_POSITIONS);
    }

    public static void validatePoint(JsonNode geometry) {
        requireGeometryObject(geometry);
        if (!"Point".equals(requiredText(geometry, "type"))) {
            throw new IllegalArgumentException("空间设施几何必须是 Point");
        }
        validateGeometry(geometry, DEFAULT_MAX_POSITIONS);
    }

    public static void validateGeometry(JsonNode geometry) {
        validateGeometry(geometry, DEFAULT_MAX_POSITIONS);
    }

    public static void validateGeometry(JsonNode geometry, int maxPositions) {
        if (maxPositions <= 0) {
            throw new IllegalArgumentException("坐标数上限必须大于 0");
        }
        PositionCounter counter = new PositionCounter(maxPositions);
        validateGeometry(geometry, counter, 0);
    }

    public static String geometryCategory(JsonNode geometry) {
        requireGeometryObject(geometry);
        return switch (requiredText(geometry, "type")) {
            case "Point", "MultiPoint" -> "POINT";
            case "LineString", "MultiLineString" -> "LINE";
            case "Polygon", "MultiPolygon" -> "POLYGON";
            case "GeometryCollection" -> "MIXED";
            default -> throw new IllegalArgumentException("不支持的 GeoJSON geometry.type");
        };
    }

    private static void validateGeometry(JsonNode geometry, PositionCounter counter, int depth) {
        requireGeometryObject(geometry);
        String type = requiredText(geometry, "type");

        switch (type) {
            case "Point" -> validatePosition(requiredArray(geometry, "coordinates"), counter);
            case "MultiPoint" -> validatePositionArray(requiredArray(geometry, "coordinates"), 1, counter);
            case "LineString" -> validatePositionArray(requiredArray(geometry, "coordinates"), 2, counter);
            case "MultiLineString" -> validateLineArray(requiredArray(geometry, "coordinates"), counter);
            case "Polygon" -> validatePolygonCoordinates(requiredArray(geometry, "coordinates"), counter);
            case "MultiPolygon" -> validateMultiPolygonCoordinates(requiredArray(geometry, "coordinates"), counter);
            case "GeometryCollection" -> validateGeometryCollection(geometry, counter, depth);
            default -> throw new IllegalArgumentException("不支持的 GeoJSON geometry.type: " + type);
        }
    }

    private static void validateGeometryCollection(JsonNode geometry, PositionCounter counter, int depth) {
        if (depth >= MAX_GEOMETRY_COLLECTION_DEPTH) {
            throw new IllegalArgumentException("GeometryCollection 嵌套过深");
        }
        JsonNode geometries = requiredArray(geometry, "geometries");
        for (JsonNode child : geometries) {
            validateGeometry(child, counter, depth + 1);
        }
    }

    private static void validateLineArray(JsonNode lines, PositionCounter counter) {
        requireNonEmpty(lines, "MultiLineString.coordinates");
        for (JsonNode line : lines) {
            requireArrayNode(line, "MultiLineString 中的线");
            validatePositionArray(line, 2, counter);
        }
    }

    private static void validatePolygonCoordinates(JsonNode rings, PositionCounter counter) {
        requireNonEmpty(rings, "Polygon.coordinates");
        for (JsonNode ring : rings) {
            validateLinearRing(ring, counter);
        }
    }

    private static void validateMultiPolygonCoordinates(JsonNode polygons, PositionCounter counter) {
        requireNonEmpty(polygons, "MultiPolygon.coordinates");
        for (JsonNode polygon : polygons) {
            requireArrayNode(polygon, "MultiPolygon 中的多边形");
            validatePolygonCoordinates(polygon, counter);
        }
    }

    private static void validateLinearRing(JsonNode ring, PositionCounter counter) {
        requireArrayNode(ring, "Polygon 线性环");
        if (ring.size() < 4) {
            throw new IllegalArgumentException("Polygon 线性环至少需要 4 个坐标位置");
        }
        for (JsonNode position : ring) {
            validatePosition(position, counter);
        }
        if (!samePosition(ring.get(0), ring.get(ring.size() - 1))) {
            throw new IllegalArgumentException("Polygon 线性环的首尾坐标必须相同");
        }
    }

    private static void validatePositionArray(JsonNode positions, int minimumSize, PositionCounter counter) {
        requireArrayNode(positions, "GeoJSON 坐标数组");
        if (positions.size() < minimumSize) {
            throw new IllegalArgumentException("GeoJSON 坐标数组长度不足");
        }
        for (JsonNode position : positions) {
            validatePosition(position, counter);
        }
    }

    private static void validatePosition(JsonNode position, PositionCounter counter) {
        requireArrayNode(position, "GeoJSON 坐标位置");
        if (position.size() != 2) {
            throw new IllegalArgumentException("GeoJSON 坐标位置必须包含 2 个数值");
        }

        Iterator<JsonNode> values = position.elements();
        int index = 0;
        while (values.hasNext()) {
            JsonNode value = values.next();
            if (!value.isNumber() || !Double.isFinite(value.doubleValue())) {
                throw new IllegalArgumentException("GeoJSON 坐标必须是有限数值");
            }
            double coordinate = value.doubleValue();
            if (index == 0 && (coordinate < -180 || coordinate > 180)) {
                throw new IllegalArgumentException("GeoJSON 经度必须位于 [-180, 180]");
            }
            if (index == 1 && (coordinate < -90 || coordinate > 90)) {
                throw new IllegalArgumentException("GeoJSON 纬度必须位于 [-90, 90]");
            }
            index++;
        }
        counter.increment();
    }

    private static boolean samePosition(JsonNode first, JsonNode last) {
        if (first.size() != last.size()) {
            return false;
        }
        for (int i = 0; i < first.size(); i++) {
            if (Double.compare(first.get(i).doubleValue(), last.get(i).doubleValue()) != 0) {
                return false;
            }
        }
        return true;
    }

    private static void requireGeometryObject(JsonNode geometry) {
        if (geometry == null || geometry.isNull() || !geometry.isObject()) {
            throw new IllegalArgumentException("GeoJSON geometry 必须是对象");
        }
    }

    private static String requiredText(JsonNode object, String fieldName) {
        JsonNode value = object.get(fieldName);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException("GeoJSON geometry." + fieldName + " 必须是非空字符串");
        }
        return value.textValue();
    }

    private static JsonNode requiredArray(JsonNode object, String fieldName) {
        JsonNode value = object.get(fieldName);
        requireArrayNode(value, "GeoJSON geometry." + fieldName);
        return value;
    }

    private static void requireArrayNode(JsonNode value, String fieldName) {
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException(fieldName + " 必须是数组");
        }
    }

    private static void requireNonEmpty(JsonNode value, String fieldName) {
        requireArrayNode(value, fieldName);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
    }

    private static final class PositionCounter {
        private final int maximum;
        private int current;

        private PositionCounter(int maximum) {
            this.maximum = maximum;
        }

        private void increment() {
            current++;
            if (current > maximum) {
                throw new IllegalArgumentException("GeoJSON 几何坐标数超过上限 " + maximum);
            }
        }
    }
}
