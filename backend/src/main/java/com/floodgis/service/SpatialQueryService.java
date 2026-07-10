package com.floodgis.service;

import com.floodgis.dto.SpatialQueryRequest;

import java.util.Map;

public interface SpatialQueryService {
    Map<String, Object> query(SpatialQueryRequest request);
}
