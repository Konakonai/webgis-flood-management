package com.floodgis.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.floodgis.entity.GeoFeature;
import com.floodgis.entity.GeoLayer;
import com.floodgis.mapper.GeoFeatureMapper;
import com.floodgis.mapper.GeoLayerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeoLayerServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private GeoLayerMapper geoLayerMapper;
    @Mock
    private GeoFeatureMapper geoFeatureMapper;

    private GeoLayerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GeoLayerServiceImpl(geoLayerMapper, geoFeatureMapper, objectMapper);
    }

    @Test
    void parsesAndPersistsFeatureCollection() throws Exception {
        JsonNode collection = objectMapper.readTree("""
                {
                  "type": "FeatureCollection",
                  "features": [{
                    "type": "Feature",
                    "properties": {"name": "测试点", "level": 2},
                    "geometry": {"type": "Point", "coordinates": [117.2, 34.3]}
                  }]
                }
                """);
        doAnswer(invocation -> {
            GeoLayer layer = invocation.getArgument(0);
            layer.setId(7L);
            return 1;
        }).when(geoLayerMapper).insert(any(GeoLayer.class));
        when(geoFeatureMapper.areGeometriesValid(any())).thenReturn(true);
        when(geoFeatureMapper.insertFeature(any(GeoFeature.class))).thenReturn(1);

        GeoLayer layer = service.uploadGeoJson("  测试图层  ", "description", collection);

        assertEquals(7L, layer.getId());
        assertEquals("测试图层", layer.getName());
        assertEquals("POINT", layer.getLayerType());
        assertEquals(1, layer.getFeatureCount());

        ArgumentCaptor<GeoFeature> featureCaptor = ArgumentCaptor.forClass(GeoFeature.class);
        verify(geoFeatureMapper).insertFeature(featureCaptor.capture());
        GeoFeature stored = featureCaptor.getValue();
        assertEquals(7L, stored.getLayerId());
        assertEquals("测试点", stored.getName());
        assertEquals("Point", objectMapper.readTree(stored.getGeomJson()).get("type").textValue());
        assertEquals(2, objectMapper.readTree(stored.getProperties()).get("level").intValue());
    }

    @Test
    void rejectsNonFeatureCollectionWithoutWritingAnything() throws Exception {
        JsonNode geometry = objectMapper.readTree("{\"type\":\"Point\",\"coordinates\":[117,34]}");

        assertThrows(IllegalArgumentException.class,
                () -> service.uploadGeoJson("错误图层", null, geometry));

        verify(geoLayerMapper, never()).insert(any());
        verify(geoFeatureMapper, never()).insertFeature(any());
    }

    @Test
    void rejectsThreeDimensionalCoordinatesBeforeDatabaseInsert() throws Exception {
        JsonNode collection = objectMapper.readTree("""
                {
                  "type": "FeatureCollection",
                  "features": [{
                    "type": "Feature",
                    "properties": {},
                    "geometry": {"type": "Point", "coordinates": [117.2, 34.3, 12.0]}
                  }]
                }
                """);

        assertThrows(IllegalArgumentException.class,
                () -> service.uploadGeoJson("三维图层", null, collection));

        verify(geoLayerMapper, never()).insert(any());
        verify(geoFeatureMapper, never()).insertFeature(any());
    }

    @Test
    void rejectsTopologicallyInvalidGeometryBeforeLayerInsert() throws Exception {
        JsonNode collection = objectMapper.readTree("""
                {
                  "type": "FeatureCollection",
                  "features": [{
                    "type": "Feature",
                    "properties": {},
                    "geometry": {
                      "type": "Polygon",
                      "coordinates": [[[117,34],[118,35],[118,34],[117,35],[117,34]]]
                    }
                  }]
                }
                """);
        when(geoFeatureMapper.areGeometriesValid(any())).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> service.uploadGeoJson("拓扑无效图层", null, collection));

        verify(geoLayerMapper, never()).insert(any());
        verify(geoFeatureMapper, never()).insertFeature(any());
    }

    @Test
    void readsStoredRowsAsStandardFeatureCollection() {
        GeoLayer layer = new GeoLayer();
        layer.setId(8L);
        when(geoLayerMapper.selectById(8L)).thenReturn(layer);

        GeoFeature stored = new GeoFeature();
        stored.setId(21L);
        stored.setLayerId(8L);
        stored.setName("补充名称");
        stored.setProperties("{\"value\":3}");
        stored.setGeomJson("{\"type\":\"Point\",\"coordinates\":[117.1,34.2]}");
        when(geoFeatureMapper.findByLayerId(8L)).thenReturn(List.of(stored));

        Map<String, Object> collection = service.getLayerFeatures(8L);

        assertEquals("FeatureCollection", collection.get("type"));
        List<?> features = (List<?>) collection.get("features");
        Map<?, ?> feature = (Map<?, ?>) features.get(0);
        assertEquals("Feature", feature.get("type"));
        assertEquals(21L, feature.get("id"));
        JsonNode properties = (JsonNode) feature.get("properties");
        assertEquals("补充名称", properties.get("name").textValue());
    }
}
