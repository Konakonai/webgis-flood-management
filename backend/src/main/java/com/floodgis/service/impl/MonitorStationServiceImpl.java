package com.floodgis.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.floodgis.entity.MonitorStation;
import com.floodgis.mapper.MonitorStationMapper;
import com.floodgis.service.MonitorStationService;
import org.springframework.stereotype.Service;

@Service
public class MonitorStationServiceImpl
        extends ServiceImpl<MonitorStationMapper, MonitorStation>
        implements MonitorStationService {
}
