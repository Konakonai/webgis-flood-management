package com.floodgis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * 前端空间联合查询的固定请求契约。
 */
@Data
public class SpatialQueryRequest {

    @JsonProperty("bufferGeoJSON")
    private JsonNode bufferGeoJSON;

    private String name;

    private String type;
}
