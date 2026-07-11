package com.floodgis.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("monitor_data")
public class MonitorData {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long stationId;
    private String dataType;
    private Double value;
    private Integer warningLevel;
    private String unit;
    private LocalDateTime recordedAt;
    private LocalDateTime createdAt;
}
