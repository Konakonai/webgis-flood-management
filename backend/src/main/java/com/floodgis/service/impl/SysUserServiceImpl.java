package com.floodgis.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.floodgis.config.ApiException;
import com.floodgis.entity.SysUser;
import com.floodgis.mapper.SysUserMapper;
import com.floodgis.service.SysUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SysUserServiceImpl
        extends ServiceImpl<SysUserMapper, SysUser>
        implements SysUserService {

    @Override
    @Transactional
    public void setEnabled(Long userId, boolean enabled, Long currentUserId) {
        // A single, stable role row serializes all account-status changes across
        // application instances so two admins cannot concurrently remove the last admin.
        if (baseMapper.lockRoleForUserStatus("ROLE_ADMIN") == null) {
            throw new IllegalStateException("系统缺少 ROLE_ADMIN 角色");
        }
        SysUser user = baseMapper.lockById(userId);
        if (user == null) throw ApiException.notFound("用户不存在");
        if (Boolean.valueOf(enabled).equals(user.getEnabled())) return;
        if (!enabled && currentUserId != null && userId.equals(currentUserId)) {
            throw ApiException.badRequest("不能禁用当前登录账号");
        }
        if (!enabled && baseMapper.hasRole(userId, "ROLE_ADMIN")
                && baseMapper.countEnabledUsersByRole("ROLE_ADMIN") <= 1) {
            throw ApiException.badRequest("不能禁用最后一个可用管理员账号");
        }
        user.setEnabled(enabled);
        baseMapper.updateById(user);
    }
}
