package com.floodgis.dto;

import lombok.Data;

/**
 * spatial_facility 的查询投影。几何以 GeoJSON 字符串读出，避免在 Java 侧引入 PostGIS 类型依赖。
 */
@Data
public class SpatialFacilityRecord {
    private String id;
    private String name;
    private String facilityType;
    private String typeName;
    private String status;
    private String statusName;
    private String manager;
    private String phone;
    private String address;
    private String waterDepth;
    private String capacity;
    private String geomJson;
}
