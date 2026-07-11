package com.floodgis.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("emergency_resource")
public class EmergencyResource {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String resourceType;
    private Double lat;
    private Double lng;
    private String address;
    private String area;
    private Integer quantity;
    private String unit;
    private String contactPerson;
    private String contactPhone;
    private String status;
    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private Object geom;
}
