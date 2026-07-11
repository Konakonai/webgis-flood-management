package com.floodgis.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_log")
public class SysLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String action;
    private String module;
    private String description;
    private String requestUri;
    private String requestMethod;
    private String ipAddress;
    private Long executionTime;
    private String status;
    private String errorMsg;
    private LocalDateTime createdAt;
}
