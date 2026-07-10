package com.floodgis.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("warning_record")
public class WarningRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long stationId;
    private Integer warningLevel;
    private String status;
    private String title;
    private String content;
    private String affectedArea;
    private String measures;
    private String createdBy;
    private String confirmedBy;
    private String publishedBy;
    private String revokedBy;
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime publishedAt;
    private LocalDateTime revokedAt;
}
