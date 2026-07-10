package com.floodgis.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.floodgis.entity.WarningRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface WarningRecordService extends IService<WarningRecord> {

    /**
     * 确认预警
     */
    WarningRecord confirm(Long id, String username);

    /**
     * 驳回预警
     */
    WarningRecord reject(Long id, String username);

    /**
     * 发布预警
     */
    WarningRecord publish(Long id, WarningRecord updateInfo, String username);

    /**
     * 撤销预警
     */
    WarningRecord revoke(Long id, String username);

    /**
     * 自动创建预警记录（由检测器调用）
     */
    WarningRecord autoCreate(Long stationId, Integer warningLevel, String title, String content);

    List<WarningRecord> history(LocalDateTime start, LocalDateTime end, String status);

    Map<String, Object> statistics();
}
