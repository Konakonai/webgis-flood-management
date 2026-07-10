package com.floodgis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkOrderStatusRequest {
    @NotBlank
    @Pattern(regexp = "PROCESSING|COMPLETED|REJECTED", message = "工单状态不合法")
    private String status;
    @Size(max = 2000)
    private String result;
    @Size(max = 1000)
    private String note;
}
