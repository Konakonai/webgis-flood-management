package com.floodgis.service.impl;

import com.floodgis.config.ApiException;
import com.floodgis.entity.SysUser;
import com.floodgis.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class SysUserServiceImplTest {
    private SysUserMapper mapper;
    private SysUserServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(SysUserMapper.class);
        service = new SysUserServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        when(mapper.lockRoleForUserStatus("ROLE_ADMIN")).thenReturn(1L);
    }

    @Test
    void refusesToDisableLastEnabledAdminWhileHoldingGlobalLock() {
        SysUser admin = user(1L, true);
        when(mapper.lockById(1L)).thenReturn(admin);
        when(mapper.hasRole(1L, "ROLE_ADMIN")).thenReturn(true);
        when(mapper.countEnabledUsersByRole("ROLE_ADMIN")).thenReturn(1L);

        ApiException error = assertThrows(ApiException.class,
                () -> service.setEnabled(1L, false, 2L));

        assertEquals(400, error.getStatus().value());
        verify(mapper).lockRoleForUserStatus("ROLE_ADMIN");
        verify(mapper, never()).updateById(any());
    }

    @Test
    void refusesSelfDisable() {
        when(mapper.lockById(7L)).thenReturn(user(7L, true));
        ApiException error = assertThrows(ApiException.class,
                () -> service.setEnabled(7L, false, 7L));
        assertEquals(400, error.getStatus().value());
    }

    private SysUser user(Long id, boolean enabled) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setEnabled(enabled);
        return user;
    }
}
