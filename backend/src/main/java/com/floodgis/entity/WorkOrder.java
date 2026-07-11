package com.floodgis.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("work_order")
public class WorkOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String type;
    private String title;
    private String description;
    private Double lat;
    private Double lng;
    private String status;
    private String priority;
    private String reporterName;
    private String reporterPhone;
    private String images;
    private Long handlerId;
    private String handlerName;
    private LocalDateTime processedAt;
    private String result;
    private String trackingCode;
    private Double waterDepthCm;
    private Long assignedResourceId;
    private LocalDateTime dispatchedAt;
    private LocalDateTime arrivedAt;
    private LocalDateTime completedAt;
    private Double routeDistance;
    private Double routeDuration;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private Object geom;
}
