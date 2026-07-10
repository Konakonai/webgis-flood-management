package com.floodgis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.floodgis.config.ApiException;
import com.floodgis.entity.WarningRecord;
import com.floodgis.mapper.WarningRecordMapper;
import com.floodgis.service.impl.WarningRecordServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WarningRecordServiceTest {
    private WarningRecordMapper mapper;
    private WarningRecordServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(WarningRecordMapper.class);
        service = new WarningRecordServiceImpl(new ObjectMapper(), mock(SimpMessagingTemplate.class));
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @Test
    void confirmMovesOnlyPendingRecord() {
        WarningRecord pending = record(1L, "PENDING");
        when(mapper.lockById(1L)).thenReturn(pending);
        when(mapper.updateById(any())).thenReturn(1);

        WarningRecord result = service.confirm(1L, "operator");

        assertEquals("CONFIRMED", result.getStatus());
        assertEquals("operator", result.getConfirmedBy());
        assertNotNull(result.getConfirmedAt());
    }

    @Test
    void invalidTransitionReturnsConflict() {
        when(mapper.lockById(1L)).thenReturn(record(1L, "CONFIRMED"));
        ApiException error = assertThrows(ApiException.class, () -> service.confirm(1L, "operator"));
        assertEquals(409, error.getStatus().value());
        verify(mapper, never()).updateById(any());
    }

    @Test
    void publishValidatesAffectedGeoJson() {
        when(mapper.lockById(1L)).thenReturn(record(1L, "CONFIRMED"));
        WarningRecord update = new WarningRecord();
        update.setAffectedArea("{\"type\":\"Point\",\"coordinates\":[117,34]}");

        ApiException error = assertThrows(ApiException.class,
                () -> service.publish(1L, update, "operator"));
        assertEquals(400, error.getStatus().value());
    }

    @Test
    void autoCreateSuppressesRecentDuplicate() {
        when(mapper.lockStationForWarning(8L)).thenReturn(8L);
        when(mapper.countRecentWarnings(8L)).thenReturn(1);
        assertNull(service.autoCreate(8L, 1, "title", "content"));
        verify(mapper, never()).insert(any());
    }

    @Test
    void autoCreatePersistsPendingWarning() {
        when(mapper.lockStationForWarning(8L)).thenReturn(8L);
        when(mapper.countRecentWarnings(8L)).thenReturn(0);
        when(mapper.insert(any())).thenAnswer(invocation -> {
            WarningRecord value = invocation.getArgument(0);
            value.setId(42L);
            return 1;
        });

        WarningRecord result = service.autoCreate(8L, 2, "title", "content");

        assertEquals(42L, result.getId());
        assertEquals("PENDING", result.getStatus());
        assertEquals("SYSTEM", result.getCreatedBy());
    }

    @Test
    void statisticsAddsTotal() {
        when(mapper.countByStatus()).thenReturn(List.of(
                Map.of("status", "PENDING", "cnt", 2L),
                Map.of("status", "PUBLISHED", "cnt", 3L)));
        Map<String, Object> stats = service.statistics();
        assertEquals(5L, stats.get("total"));
        assertEquals(2L, stats.get("PENDING"));
    }

    private WarningRecord record(Long id, String status) {
        WarningRecord record = new WarningRecord();
        record.setId(id);
        record.setStatus(status);
        return record;
    }
}
