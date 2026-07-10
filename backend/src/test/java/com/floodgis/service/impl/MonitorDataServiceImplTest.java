package com.floodgis.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.floodgis.config.ApiException;
import com.floodgis.mapper.MonitorDataMapper;
import com.floodgis.service.MonitorStationService;
import com.floodgis.service.SysConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class MonitorDataServiceImplTest {
    private MonitorDataServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MonitorDataServiceImpl(
                mock(MonitorStationService.class),
                mock(SysConfigService.class),
                new ObjectMapper());
        ReflectionTestUtils.setField(service, "baseMapper", mock(MonitorDataMapper.class));
    }

    @Test
    void mapsSeedStationTypesCorrectly() {
        assertEquals("RAINFALL", service.determineDataType("RAIN_GAUGE"));
        assertEquals("FLOW", service.determineDataType("FLOW_METER"));
        assertEquals("WATER_LEVEL", service.determineDataType("WATER_GAUGE"));
    }

    @Test
    void rejectsUnboundedHistoryQuery() {
        ApiException error = assertThrows(ApiException.class,
                () -> service.getHistory(1L, "WATER_LEVEL", 721));
        assertEquals(400, error.getStatus().value());
    }
}
