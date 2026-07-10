package com.floodgis.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("monitor_station")
public class MonitorStation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String stationType;
    private Double lat;
    private Double lng;
    private String address;
    private String area;
    private String status;
    private LocalDate installDate;
    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private Object geom;
}
