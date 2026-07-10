package com.floodgis.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.floodgis.dto.SpatialFacilityRecord;
import com.floodgis.dto.SpatialQueryRequest;
import com.floodgis.mapper.SpatialFacilityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpatialQueryServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private SpatialFacilityMapper spatialFacilityMapper;

    private SpatialQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SpatialQueryServiceImpl(spatialFacilityMapper, objectMapper);
    }

    @Test
    void trimsFiltersAndReturnsUnwrappedPointFeatureCollection() {
        SpatialQueryRequest request = new SpatialQueryRequest();
        request.setName("  泵站  ");
        request.setType("pump");

        SpatialFacilityRecord record = new SpatialFacilityRecord();
        record.setId("F002");
        record.setName("云龙湖东路防汛雨水泵站");
        record.setFacilityType("pump");
        record.setTypeName("雨水泵站");
        record.setStatus("running");
        record.setStatusName("正在运行");
        record.setManager("泉山区水务局");
        record.setPhone("13888889902");
        record.setAddress("云龙湖东岸");
        record.setCapacity("5000 m³/h");
        record.setGeomJson("{\"type\":\"Point\",\"coordinates\":[117.142,34.235]}");
        when(spatialFacilityMapper.search("泵站", "pump", null)).thenReturn(List.of(record));

        Map<String, Object> result = service.query(request);

        assertEquals("FeatureCollection", result.get("type"));
        List<?> features = (List<?>) result.get("features");
        assertEquals(1, features.size());
        Map<?, ?> feature = (Map<?, ?>) features.get(0);
        JsonNode properties = (JsonNode) feature.get("properties");
        assertEquals("F002", properties.get("id").textValue());
        assertEquals("pump", properties.get("type").textValue());
        assertEquals("5000 m³/h", properties.get("capacity").textValue());
        assertEquals(true, properties.has("waterDepth"));
        assertEquals(true, properties.get("waterDepth").isNull());
        verify(spatialFacilityMapper).search("泵站", "pump", null);
    }

    @Test
    void passesPolygonAsOneBoundJsonString() throws Exception {
        SpatialQueryRequest request = new SpatialQueryRequest();
        request.setType("all");
        request.setBufferGeoJSON(objectMapper.readTree("""
                {
                  "type": "Polygon",
                  "coordinates": [[[117.0,34.0],[118.0,34.0],[118.0,35.0],[117.0,34.0]]]
                }
                """));
        String expectedJson = request.getBufferGeoJSON().toString();
        when(spatialFacilityMapper.isValidBuffer(expectedJson)).thenReturn(true);
        when(spatialFacilityMapper.search("", "all", expectedJson)).thenReturn(List.of());

        service.query(request);

        verify(spatialFacilityMapper).search("", "all", expectedJson);
    }

    @Test
    void rejectsTopologicallyInvalidPolygonBeforeSearch() throws Exception {
        SpatialQueryRequest request = new SpatialQueryRequest();
        request.setBufferGeoJSON(objectMapper.readTree("""
                {
                  "type": "Polygon",
                  "coordinates": [[[117.0,34.0],[118.0,35.0],[118.0,34.0],[117.0,35.0],[117.0,34.0]]]
                }
                """));
        String geometryJson = request.getBufferGeoJSON().toString();
        when(spatialFacilityMapper.isValidBuffer(geometryJson)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.query(request));

        verify(spatialFacilityMapper).isValidBuffer(geometryJson);
    }

    @Test
    void rejectsInvalidTypeBeforeCallingDatabase() {
        SpatialQueryRequest request = new SpatialQueryRequest();
        request.setType("pump' OR '1'='1");

        assertThrows(IllegalArgumentException.class, () -> service.query(request));
        verifyNoInteractions(spatialFacilityMapper);
    }

    @Test
    void rejectsUnclosedPolygonBeforeCallingDatabase() throws Exception {
        SpatialQueryRequest request = new SpatialQueryRequest();
        request.setBufferGeoJSON(objectMapper.readTree("""
                {
                  "type": "Polygon",
                  "coordinates": [[[117.0,34.0],[118.0,34.0],[118.0,35.0],[117.0,35.0]]]
                }
                """));

        assertThrows(IllegalArgumentException.class, () -> service.query(request));
        verifyNoInteractions(spatialFacilityMapper);
    }
}
