package com.floodgis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("work_order_status_history")
public class WorkOrderStatusHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workOrderId;
    private String fromStatus;
    private String toStatus;
    private String operatorName;
    private String note;
    private LocalDateTime createdAt;
}
