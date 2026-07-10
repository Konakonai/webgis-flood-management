package com.floodgis.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkOrderUpdateRequest {
    @Size(max = 200)
    private String title;
    @Size(max = 4000)
    private String description;
    @Pattern(regexp = "LOW|NORMAL|HIGH|URGENT", message = "优先级不合法")
    private String priority;
    @Size(max = 2000)
    private String result;
}
