package com.floodgis.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.floodgis.entity.SysConfig;

public interface SysConfigService extends IService<SysConfig> {
    String getConfigValue(String key);
    boolean updateConfigValue(String key, String value);
}
