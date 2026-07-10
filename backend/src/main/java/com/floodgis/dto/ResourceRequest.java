package com.floodgis.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResourceRequest {
    @NotBlank(message = "资源名称不能为空")
    @Size(max = 100)
    private String name;

    @NotBlank(message = "资源类型不能为空")
    @Pattern(regexp = "PUMP_TRUCK|SANDBAG|TEAM|WAREHOUSE|SHELTER", message = "资源类型不合法")
    private String resourceType;

    @NotNull @DecimalMin("-90.0") @DecimalMax("90.0")
    private Double lat;
    @NotNull @DecimalMin("-180.0") @DecimalMax("180.0")
    private Double lng;

    @Size(max = 200)
    private String address;
    @Size(max = 100)
    private String area;
    @Min(value = 0, message = "资源数量不能为负数")
    private Integer quantity = 0;
    @Size(max = 20)
    private String unit;
    @Size(max = 50)
    private String contactPerson;
    @Size(max = 20)
    private String contactPhone;
    @NotBlank(message = "资源状态不能为空")
    @Pattern(regexp = "AVAILABLE|DISPATCHED|DEPLETED", message = "资源状态不合法")
    private String status = "AVAILABLE";
    @Size(max = 1000)
    private String description;
}
