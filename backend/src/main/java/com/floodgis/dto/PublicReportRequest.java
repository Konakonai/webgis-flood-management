package com.floodgis.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PublicReportRequest {
    @NotNull @DecimalMin("-180.0") @DecimalMax("180.0")
    private Double lng;
    @NotNull @DecimalMin("-90.0") @DecimalMax("90.0")
    private Double lat;
    @NotNull @DecimalMin("0.0") @DecimalMax("1000.0")
    private Double depth;
    @Size(max = 2000, message = "现场描述不能超过2000个字符")
    private String description;
    @Size(max = 50)
    private String reporterName;
    @Size(max = 20)
    private String reporterPhone;
    // Optional data URL for compatibility with the current mobile model.
    @Size(max = 7_000_000, message = "图片数据过大")
    private String image;
}
