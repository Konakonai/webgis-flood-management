package com.floodgis.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fasterxml.jackson.databind.JsonNode;
import com.floodgis.entity.GeoLayer;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

public interface GeoLayerService extends IService<GeoLayer> {
    GeoLayer uploadLayer(String name, String description, MultipartFile file) throws IOException;
    GeoLayer uploadGeoJson(String name, String description, JsonNode geoJson);
    Map<String, Object> getLayerFeatures(Long layerId);
}
