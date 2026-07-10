package com.floodgis.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("geo_feature")
public class GeoFeature {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long layerId;
    private String name;
    private String properties;
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private String geomJson;
}
