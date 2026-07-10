package com.floodgis.service;

import com.floodgis.entity.MonitorData;
import com.floodgis.entity.MonitorStation;
import com.floodgis.entity.WarningRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 预警检测器
 * 监测数据写入后自动判断是否触发预警
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarningDetector {

    private final WarningRecordService warningRecordService;
    private final MonitorStationService monitorStationService;

    /**
     * 检测单条监测数据是否触发预警
     * 若 warningLevel >= 1 且30分钟内无记录，自动创建预警
     *
     * @param data 监测数据
     * @return 创建的预警记录，未触发则返回 null
     */
    public WarningRecord detectAndCreate(MonitorData data) {
        if (data.getWarningLevel() == null || data.getWarningLevel() < 1) {
            return null; // 正常数据，不触发预警
        }

        MonitorStation station = monitorStationService.getById(data.getStationId());
        String stationName = station != null ? station.getName() : "未知站点";
        String levelText = data.getWarningLevel() == 2 ? "危险" : "预警";
        String typeText = "WATER_LEVEL".equals(data.getDataType()) ? "水位" : "雨量";

        String title = String.format("[%s] %s站%s%s告警",
                levelText, stationName, typeText,
                data.getValue() != null ? " " + data.getValue() + data.getUnit() : "");

        String content = String.format("站点【%s】%s监测值达到%s阈值: %.2f %s，触发时间: %s。请及时复核处理。",
                stationName, typeText, levelText, data.getValue(), data.getUnit(),
                data.getRecordedAt() != null
                        ? data.getRecordedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        : LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        return warningRecordService.autoCreate(data.getStationId(), data.getWarningLevel(), title, content);
    }
}
