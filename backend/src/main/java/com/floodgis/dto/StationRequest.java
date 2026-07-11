package com.floodgis.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class StationRequest {
    @NotBlank(message = "站点名称不能为空")
    @Size(max = 100, message = "站点名称不能超过100个字符")
    private String name;

    @NotBlank(message = "站点类型不能为空")
    @Pattern(regexp = "RAIN_GAUGE|WATER_GAUGE|FLOW_METER|PUMP_STATION", message = "站点类型不合法")
    private String stationType;

    @NotNull(message = "纬度不能为空")
    @DecimalMin(value = "-90.0", message = "纬度不能小于-90")
    @DecimalMax(value = "90.0", message = "纬度不能大于90")
    private Double lat;

    @NotNull(message = "经度不能为空")
    @DecimalMin(value = "-180.0", message = "经度不能小于-180")
    @DecimalMax(value = "180.0", message = "经度不能大于180")
    private Double lng;

    @Size(max = 200)
    private String address;

    @Size(max = 100)
    private String area;

    @Pattern(regexp = "ACTIVE|INACTIVE|MAINTENANCE", message = "站点状态不合法")
    private String status = "ACTIVE";

    private LocalDate installDate;

    @Size(max = 1000)
    private String description;
}
