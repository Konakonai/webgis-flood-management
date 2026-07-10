package com.floodgis.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.floodgis.dto.LoginRequest;
import com.floodgis.dto.RegisterRequest;
import com.floodgis.entity.SysUser;
import com.floodgis.entity.SysRole;
import com.floodgis.mapper.SysRoleMapper;
import com.floodgis.mapper.SysUserMapper;
import com.floodgis.security.JwtUtils;
import com.floodgis.config.ApiException;
import com.floodgis.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    @Override
    public Map<String, Object> login(LoginRequest request) {
        // 查询用户
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, request.getUsername())
        );

        if (user == null) {
            throw ApiException.unauthorized("用户名或密码错误");
        }

        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw ApiException.unauthorized("账号已被禁用");
        }

        // 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw ApiException.unauthorized("用户名或密码错误");
        }

        // 获取角色
        List<String> roles = sysUserMapper.findRolesByUserId(user.getId());

        // 生成 Token
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), roles);

        // 构建返回数据
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        result.put("realName", user.getRealName());
        result.put("roles", roles);

        return result;
    }

    @Override
    @Transactional
    public SysUser register(RegisterRequest request) {
        // 检查用户名是否已存在
        Long count = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, request.getUsername())
        );
        if (count > 0) {
            throw ApiException.conflict("用户名已存在");
        }

        // 创建用户
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setEnabled(true);

        sysUserMapper.insert(user);

        Long viewerRoleId = sysUserMapper.findRoleIdByCode("ROLE_VIEWER");
        if (viewerRoleId == null) {
            throw new IllegalStateException("系统缺少 ROLE_VIEWER 角色");
        }
        sysUserMapper.insertUserRole(user.getId(), viewerRoleId);

        // 清除密码后返回
        user.setPassword(null);
        return user;
    }

    @Override
    public SysUser getCurrentUser(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user != null) {
            user.setPassword(null);
        }
        return user;
    }
}
