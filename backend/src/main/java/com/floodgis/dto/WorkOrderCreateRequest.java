package com.floodgis.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkOrderCreateRequest {
    @NotBlank
    @Pattern(regexp = "REPORT|WARNING|DISPATCH", message = "工单类型不合法")
    private String type;
    @NotBlank @Size(max = 200)
    private String title;
    @Size(max = 4000)
    private String description;
    @DecimalMin("-90.0") @DecimalMax("90.0")
    private Double lat;
    @DecimalMin("-180.0") @DecimalMax("180.0")
    private Double lng;
    @Pattern(regexp = "LOW|NORMAL|HIGH|URGENT", message = "优先级不合法")
    private String priority = "NORMAL";
    @Size(max = 50)
    private String reporterName;
    @Size(max = 20)
    private String reporterPhone;
}
