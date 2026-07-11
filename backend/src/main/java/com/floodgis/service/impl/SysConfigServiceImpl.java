package com.floodgis.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.floodgis.entity.SysConfig;
import com.floodgis.mapper.SysConfigMapper;
import com.floodgis.service.SysConfigService;
import org.springframework.stereotype.Service;

@Service
public class SysConfigServiceImpl
        extends ServiceImpl<SysConfigMapper, SysConfig>
        implements SysConfigService {

    @Override
    public String getConfigValue(String key) {
        SysConfig config = this.getOne(
                new LambdaQueryWrapper<SysConfig>()
                        .eq(SysConfig::getConfigKey, key)
        );
        return config != null ? config.getConfigValue() : null;
    }

    @Override
    public boolean updateConfigValue(String key, String value) {
        LambdaUpdateWrapper<SysConfig> wrapper = new LambdaUpdateWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key)
                .set(SysConfig::getConfigValue, value);
        return this.update(wrapper);
    }
}
