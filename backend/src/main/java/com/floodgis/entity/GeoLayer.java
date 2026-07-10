package com.floodgis.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("geo_layer")
public class GeoLayer {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String layerType;
    private String description;
    private String coordSystem;
    private Integer featureCount;
    private String fileName;
    private Long fileSize;
    private String uploadBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
