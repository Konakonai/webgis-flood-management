package com.floodgis.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.floodgis.config.ApiException;
import com.floodgis.entity.MonitorData;
import com.floodgis.entity.MonitorStation;
import com.floodgis.mapper.MonitorDataMapper;
import com.floodgis.service.MonitorDataService;
import com.floodgis.service.MonitorStationService;
import com.floodgis.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MonitorDataServiceImpl extends ServiceImpl<MonitorDataMapper, MonitorData>
        implements MonitorDataService {

    private final MonitorStationService monitorStationService;
    private final SysConfigService configService;
    private final ObjectMapper objectMapper;

    @Override
    public List<MonitorData> getLatestByStations() {
        return baseMapper.findLatestByStations();
    }

    @Override
    public List<MonitorData> getHistory(Long stationId, String dataType, Integer hours) {
        if (stationId == null || stationId < 1) throw ApiException.badRequest("站点ID不合法");
        if (!List.of("WATER_LEVEL", "RAINFALL", "FLOW").contains(dataType)) {
            throw ApiException.badRequest("监测数据类型不合法");
        }
        if (hours == null || hours < 1 || hours > 720) {
            throw ApiException.badRequest("查询时长必须为1到720小时");
        }
        return baseMapper.findHistory(stationId, dataType, hours);
    }

    @Override
    public List<Map<String, Object>> summarize(Integer hours) {
        if (hours == null || hours < 1 || hours > 720) {
            throw ApiException.badRequest("统计时长必须为1到720小时");
        }
        return baseMapper.summarize(hours);
    }

    @Override
    public List<MonitorData> generateMockData() {
        List<MonitorStation> stations = monitorStationService.list(
                new LambdaQueryWrapper<MonitorStation>().eq(MonitorStation::getStatus, "ACTIVE"));
        if (stations.isEmpty()) return List.of();

        Thresholds thresholds = loadThresholds();
        long baseTime = System.currentTimeMillis() / 1000;
        LocalDateTime now = LocalDateTime.now();
        List<MonitorData> batch = new ArrayList<>(stations.size());

        for (MonitorStation station : stations) {
            String dataType = determineDataType(station.getStationType());
            Simulation simulation = simulationFor(dataType);
            double value = simulation.base + simulation.amplitude
                    * Math.sin(baseTime * 0.05 + station.getId())
                    + (Math.random() - 0.5) * simulation.amplitude * 0.3;
            value = Math.max(0, Math.round(value * 100.0) / 100.0);

            MonitorData data = new MonitorData();
            data.setStationId(station.getId());
            data.setDataType(dataType);
            data.setValue(value);
            data.setWarningLevel(determineWarningLevel(dataType, value, thresholds));
            data.setUnit(simulation.unit);
            data.setRecordedAt(now);
            data.setCreatedAt(now);
            batch.add(data);
        }
        saveBatch(batch, 100);
        return batch;
    }

    String determineDataType(String stationType) {
        if (stationType == null) return "WATER_LEVEL";
        return switch (stationType.toUpperCase()) {
            case "RAIN_GAUGE", "RAINFALL", "RAIN" -> "RAINFALL";
            case "FLOW_METER", "FLOW" -> "FLOW";
            default -> "WATER_LEVEL";
        };
    }

    private Simulation simulationFor(String dataType) {
        return switch (dataType) {
            case "RAINFALL" -> new Simulation(25.0, 20.0, "mm/h");
            case "FLOW" -> new Simulation(18.0, 8.0, "m³/s");
            default -> new Simulation(0.8, 0.6, "m");
        };
    }

    private int determineWarningLevel(String dataType, double value, Thresholds thresholds) {
        if ("WATER_LEVEL".equals(dataType)) {
            if (value >= thresholds.waterDanger) return 2;
            if (value >= thresholds.waterWarning) return 1;
        } else if ("RAINFALL".equals(dataType)) {
            if (value >= thresholds.rainDanger) return 2;
            if (value >= thresholds.rainWarning) return 1;
        }
        return 0;
    }

    private Thresholds loadThresholds() {
        return new Thresholds(
                threshold("water_level_warning", 1.0),
                threshold("water_level_danger", 1.5),
                threshold("rain_warning", 30.0),
                threshold("rain_danger", 50.0));
    }

    private double threshold(String key, double fallback) {
        String json = configService.getConfigValue(key);
        if (json == null) return fallback;
        try {
            JsonNode value = objectMapper.readTree(json).get("value");
            return value != null && value.isNumber() ? value.asDouble() : fallback;
        } catch (Exception e) {
            log.warn("配置 {} 不是有效阈值 JSON，使用默认值 {}", key, fallback);
            return fallback;
        }
    }

    private record Simulation(double base, double amplitude, String unit) { }
    private record Thresholds(double waterWarning, double waterDanger,
                              double rainWarning, double rainDanger) { }
}
