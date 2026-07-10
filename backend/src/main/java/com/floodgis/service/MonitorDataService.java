package com.floodgis.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.floodgis.entity.MonitorData;

import java.util.List;
import java.util.Map;

public interface MonitorDataService extends IService<MonitorData> {

    /**
     * 获取所有站点最新监测数据
     */
    List<MonitorData> getLatestByStations();

    /**
     * 查询指定站点历史数据
     */
    List<MonitorData> getHistory(Long stationId, String dataType, Integer hours);

    /**
     * 生成并保存模拟监测数据
     */
    List<MonitorData> generateMockData();

    List<Map<String, Object>> summarize(Integer hours);
}
