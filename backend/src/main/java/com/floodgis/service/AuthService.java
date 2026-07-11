package com.floodgis.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.floodgis.dto.LoginRequest;
import com.floodgis.dto.RegisterRequest;
import com.floodgis.entity.SysUser;

import java.util.Map;

public interface AuthService {
    Map<String, Object> login(LoginRequest request);
    SysUser register(RegisterRequest request);
    SysUser getCurrentUser(Long userId);
}
