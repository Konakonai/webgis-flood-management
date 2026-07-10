package com.floodgis.service.impl;

import com.floodgis.config.ApiException;
import com.floodgis.entity.EmergencyResource;
import com.floodgis.mapper.EmergencyResourceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class EmergencyResourceServiceImplTest {
    private EmergencyResourceMapper mapper;
    private EmergencyResourceServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(EmergencyResourceMapper.class);
        service = new EmergencyResourceServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @Test
    void directCrudCannotReleaseDispatchedResource() {
        when(mapper.lockById(2L)).thenReturn(resource(2L, "DISPATCHED"));
        ApiException error = assertThrows(ApiException.class,
                () -> service.updateManaged(2L, resource(null, "AVAILABLE")));
        assertEquals(409, error.getStatus().value());
        verify(mapper, never()).updateById(any());
    }

    @Test
    void cannotDeleteResourceAssignedToProcessingOrder() {
        when(mapper.lockById(2L)).thenReturn(resource(2L, "DISPATCHED"));
        when(mapper.countProcessingAssignments(2L)).thenReturn(1L);
        ApiException error = assertThrows(ApiException.class,
                () -> service.deleteManaged(2L));
        assertEquals(409, error.getStatus().value());
        verify(mapper, never()).deleteById(2L);
    }

    private EmergencyResource resource(Long id, String status) {
        EmergencyResource resource = new EmergencyResource();
        resource.setId(id);
        resource.setStatus(status);
        return resource;
    }
}
