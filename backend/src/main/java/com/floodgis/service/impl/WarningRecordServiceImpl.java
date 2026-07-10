package com.floodgis.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.floodgis.config.ApiException;
import com.floodgis.entity.WarningRecord;
import com.floodgis.mapper.WarningRecordMapper;
import com.floodgis.service.WarningRecordService;
import com.floodgis.service.support.GeoJsonValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarningRecordServiceImpl extends ServiceImpl<WarningRecordMapper, WarningRecord>
        implements WarningRecordService {

    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional
    public WarningRecord confirm(Long id, String username) {
        WarningRecord record = requireState(id, "PENDING", "仅待复核状态的预警可确认");
        record.setStatus("CONFIRMED");
        record.setConfirmedBy(username);
        record.setConfirmedAt(LocalDateTime.now());
        updateById(record);
        publishAfterCommit(record);
        return record;
    }

    @Override
    @Transactional
    public WarningRecord reject(Long id, String username) {
        WarningRecord record = requireState(id, "PENDING", "仅待复核状态的预警可驳回");
        record.setStatus("REJECTED");
        record.setRevokedBy(username);
        record.setRevokedAt(LocalDateTime.now());
        updateById(record);
        publishAfterCommit(record);
        return record;
    }

    @Override
    @Transactional
    public WarningRecord publish(Long id, WarningRecord updateInfo, String username) {
        WarningRecord record = requireState(id, "CONFIRMED", "仅已确认状态的预警可发布");
        if (updateInfo != null) {
            if (hasText(updateInfo.getTitle())) record.setTitle(updateInfo.getTitle().trim());
            if (hasText(updateInfo.getContent())) record.setContent(updateInfo.getContent());
            if (hasText(updateInfo.getMeasures())) record.setMeasures(updateInfo.getMeasures());
            if (hasText(updateInfo.getAffectedArea())) {
                validateAffectedArea(updateInfo.getAffectedArea());
                record.setAffectedArea(updateInfo.getAffectedArea());
            }
        }
        record.setStatus("PUBLISHED");
        record.setPublishedBy(username);
        record.setPublishedAt(LocalDateTime.now());
        updateById(record);
        publishAfterCommit(record);
        return record;
    }

    @Override
    @Transactional
    public WarningRecord revoke(Long id, String username) {
        WarningRecord record = requireState(id, "PUBLISHED", "仅已发布状态的预警可撤销");
        record.setStatus("REVOKED");
        record.setRevokedBy(username);
        record.setRevokedAt(LocalDateTime.now());
        updateById(record);
        publishAfterCommit(record);
        return record;
    }

    @Override
    @Transactional
    public WarningRecord autoCreate(Long stationId, Integer warningLevel, String title, String content) {
        if (baseMapper.lockStationForWarning(stationId) == null) {
            throw ApiException.notFound("监测站点不存在");
        }
        if (baseMapper.countRecentWarnings(stationId) > 0) return null;
        WarningRecord record = new WarningRecord();
        record.setStationId(stationId);
        record.setWarningLevel(warningLevel);
        record.setStatus("PENDING");
        record.setTitle(title);
        record.setContent(content);
        record.setCreatedAt(LocalDateTime.now());
        record.setCreatedBy("SYSTEM");
        save(record);
        publishAfterCommit(record);
        return record;
    }

    @Override
    public List<WarningRecord> history(LocalDateTime start, LocalDateTime end, String status) {
        if (start == null || end == null || !start.isBefore(end)) {
            throw ApiException.badRequest("历史回溯时间范围不合法");
        }
        if (start.isBefore(end.minusDays(366))) {
            throw ApiException.badRequest("单次历史回溯范围不能超过366天");
        }
        LambdaQueryWrapper<WarningRecord> query = new LambdaQueryWrapper<WarningRecord>()
                .between(WarningRecord::getCreatedAt, start, end);
        if (hasText(status)) query.eq(WarningRecord::getStatus, status);
        query.orderByAsc(WarningRecord::getCreatedAt);
        return list(query);
    }

    @Override
    public Map<String, Object> statistics() {
        Map<String, Object> result = new HashMap<>();
        long total = 0;
        for (Map<String, Object> row : baseMapper.countByStatus()) {
            String status = String.valueOf(row.get("status"));
            long count = ((Number) row.get("cnt")).longValue();
            result.put(status, count);
            total += count;
        }
        result.put("total", total);
        return result;
    }

    private WarningRecord requireState(Long id, String expected, String message) {
        WarningRecord record = baseMapper.lockById(id);
        if (record == null) throw ApiException.notFound("预警记录不存在");
        if (!expected.equals(record.getStatus())) {
            throw ApiException.conflict(message + "，当前状态: " + record.getStatus());
        }
        return record;
    }

    private void validateAffectedArea(String geoJson) {
        if (geoJson.length() > 1_000_000) throw ApiException.badRequest("影响范围 GeoJSON 过大");
        try {
            JsonNode root = objectMapper.readTree(geoJson);
            GeoJsonValidator.validatePolygonOrMultiPolygon(root);
            if (!Boolean.TRUE.equals(baseMapper.isValidAffectedArea(geoJson))) {
                throw ApiException.badRequest("影响范围多边形拓扑无效");
            }
        } catch (ApiException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("影响范围 GeoJSON 不合法: " + e.getMessage());
        } catch (Exception e) {
            throw ApiException.badRequest("影响范围不是有效 GeoJSON");
        }
    }

    private void publishAfterCommit(WarningRecord record) {
        Runnable publish = () -> messagingTemplate.convertAndSend("/topic/warning", record);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { publish.run(); }
            });
        } else {
            publish.run();
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
