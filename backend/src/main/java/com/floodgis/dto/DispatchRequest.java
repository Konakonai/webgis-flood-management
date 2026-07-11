package com.floodgis.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DispatchRequest {
    @NotNull @Min(1)
    private Long resourceId;
    private Long handlerId;
    @Size(max = 50)
    private String handlerName;
    @Min(0)
    private Double routeDistance;
    @Min(0)
    private Double routeDuration;
}
