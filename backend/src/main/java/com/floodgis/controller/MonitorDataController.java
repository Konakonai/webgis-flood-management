package com.floodgis.controller;

import com.floodgis.dto.Result;
import com.floodgis.entity.MonitorData;
import com.floodgis.service.MonitorDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 监测数据控制器
 * 提供监测数据的查询接口（历史数据、最新数据等）
 */
@RestController
@RequestMapping("/api/monitor")
@RequiredArgsConstructor
public class MonitorDataController {

    private final MonitorDataService monitorDataService;

    /**
     * 获取所有站点最新监测数据
     * 用于态势大屏初始化
     */
    @GetMapping("/latest")
    public Result<List<MonitorData>> getLatest() {
        List<MonitorData> data = monitorDataService.getLatestByStations();
        return Result.success(data);
    }

    /**
     * 查询指定站点的历史监测数据
     *
     * @param stationId 站点ID
     * @param dataType  数据类型（WATER_LEVEL/RAINFALL/FLOW），可选
     * @param hours     查询最近N小时，默认24小时
     */
    @GetMapping("/history")
    public Result<List<MonitorData>> getHistory(
            @RequestParam Long stationId,
            @RequestParam(required = false, defaultValue = "WATER_LEVEL") String dataType,
            @RequestParam(required = false, defaultValue = "24") Integer hours) {
        List<MonitorData> data = monitorDataService.getHistory(stationId, dataType, hours);
        return Result.success(data);
    }

    @GetMapping("/summary")
    public Result<List<Map<String, Object>>> summary(
            @RequestParam(required = false, defaultValue = "24") Integer hours) {
        return Result.success(monitorDataService.summarize(hours));
    }
}
