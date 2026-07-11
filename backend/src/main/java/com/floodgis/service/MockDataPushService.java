package com.floodgis.service;

import com.floodgis.entity.MonitorData;
import com.floodgis.entity.MonitorStation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模拟监测数据定时推送服务
 * 每5秒生成一条模拟监测数据，通过 WebSocket 推送给前端
 * 同时检测是否触发预警
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.mock-data", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MockDataPushService {

    private final MonitorDataService monitorDataService;
    private final MonitorStationService monitorStationService;
    private final WarningDetector warningDetector;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 每5秒执行一次：生成模拟数据 → 保存数据库 → WebSocket推送 → 预警检测
     */
    @Scheduled(fixedDelayString = "${app.mock-data.push-interval-ms:5000}")
    public void pushMonitorData() {
        try {
            // Only publish and evaluate the samples inserted by this scheduler run.
            // Querying the all-time "latest" rows would replay stale alarms after a
            // station is disabled or when no new sample was produced.
            List<MonitorData> latestData = monitorDataService.generateMockData();
            if (latestData.isEmpty()) return;

            for (MonitorData data : latestData) {
                MonitorStation station = monitorStationService.getById(data.getStationId());

                Map<String, Object> message = buildMessage(data, station);
                messagingTemplate.convertAndSend("/topic/monitor", message);

                // 3. 检测是否触发预警
                warningDetector.detectAndCreate(data);
            }
        } catch (Exception e) {
            log.error("模拟数据推送异常", e);
        }
    }

    /**
     * 构建监测数据消息
     */
    private Map<String, Object> buildMessage(MonitorData data, MonitorStation station) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("stationId", data.getStationId());
        msg.put("stationName", station != null ? station.getName() : "未知站点");
        msg.put("type", data.getDataType());
        msg.put("value", data.getValue());
        msg.put("unit", data.getUnit());
        msg.put("warningLevel", data.getWarningLevel());
        msg.put("timestamp", data.getRecordedAt() != null
                ? data.getRecordedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        msg.put("lat", station != null ? station.getLat() : null);
        msg.put("lng", station != null ? station.getLng() : null);
        return msg;
    }

}
