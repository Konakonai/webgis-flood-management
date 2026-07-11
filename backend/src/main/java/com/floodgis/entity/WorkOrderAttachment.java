package com.floodgis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("work_order_attachment")
public class WorkOrderAttachment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workOrderId;
    private String originalName;
    private String storedName;
    private String contentType;
    private Long fileSize;
    private String relativePath;
    private LocalDateTime createdAt;
}
