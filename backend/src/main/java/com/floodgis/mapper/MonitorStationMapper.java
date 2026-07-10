package com.floodgis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.floodgis.entity.MonitorStation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MonitorStationMapper extends BaseMapper<MonitorStation> {
}
