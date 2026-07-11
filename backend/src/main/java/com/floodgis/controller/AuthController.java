package com.floodgis.controller;

import com.floodgis.config.ApiException;
import com.floodgis.dto.LoginRequest;
import com.floodgis.dto.RegisterRequest;
import com.floodgis.dto.Result;
import com.floodgis.entity.SysUser;
import com.floodgis.security.JwtUserDetails;
import com.floodgis.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        Map<String, Object> result = authService.login(request);
        return Result.success("登录成功", result);
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<SysUser> register(@Valid @RequestBody RegisterRequest request) {
        SysUser user = authService.register(request);
        return Result.success("注册成功", user);
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/me")
    public Result<SysUser> me(@AuthenticationPrincipal JwtUserDetails userDetails) {
        if (userDetails == null) {
            throw ApiException.unauthorized("未登录");
        }
        SysUser user = authService.getCurrentUser(userDetails.getUserId());
        if (user == null) {
            throw ApiException.notFound("用户不存在");
        }
        return Result.success(user);
    }
}
