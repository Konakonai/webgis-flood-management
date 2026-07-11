package com.floodgis.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.floodgis.entity.SysUser;

public interface SysUserService extends IService<SysUser> {
    void setEnabled(Long userId, boolean enabled, Long currentUserId);
}
