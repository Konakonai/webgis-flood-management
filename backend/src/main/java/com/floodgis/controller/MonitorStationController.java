package com.floodgis.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.floodgis.aspect.LogOperation;
import com.floodgis.config.ApiException;
import com.floodgis.dto.PumpStationView;
import com.floodgis.dto.Result;
import com.floodgis.dto.StationRequest;
import com.floodgis.entity.EmergencyResource;
import com.floodgis.entity.MonitorStation;
import com.floodgis.service.EmergencyResourceService;
import com.floodgis.service.MonitorStationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stations")
@RequiredArgsConstructor
@Validated
public class MonitorStationController {

    private final MonitorStationService stationService;
    private final EmergencyResourceService resourceService;

    /** Fixed public pump-station contract used by the existing frontend. */
    @GetMapping(params = {"!page", "!size", "!name", "!stationType", "!status"})
    public List<PumpStationView> publicPumpStations() {
        return resourceService.list(new LambdaQueryWrapper<EmergencyResource>()
                        .eq(EmergencyResource::getResourceType, "PUMP_TRUCK")
                        .orderByAsc(EmergencyResource::getId))
                .stream()
                .map(this::toPumpView)
                .toList();
    }

    @GetMapping(params = "page")
    public Result<Page<MonitorStation>> list(
            @RequestParam @Min(1) Integer page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String stationType,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<MonitorStation> wrapper = new LambdaQueryWrapper<>();
        if (hasText(name)) wrapper.like(MonitorStation::getName, name.trim());
        if (hasText(stationType)) wrapper.eq(MonitorStation::getStationType, stationType);
        if (hasText(status)) wrapper.eq(MonitorStation::getStatus, status);
        wrapper.orderByDesc(MonitorStation::getCreatedAt);
        return Result.success(stationService.page(new Page<>(page, size), wrapper));
    }

    @GetMapping("/{id}")
    public Result<MonitorStation> getById(@PathVariable Long id) {
        MonitorStation station = stationService.getById(id);
        if (station == null) throw ApiException.notFound("站点不存在");
        return Result.success(station);
    }

    @PostMapping
    @LogOperation(action = "CREATE", module = "STATION", description = "新增监测站点")
    public Result<MonitorStation> create(@Valid @RequestBody StationRequest request) {
        MonitorStation station = apply(new MonitorStation(), request);
        stationService.save(station);
        return Result.success("新增成功", station);
    }

    @PutMapping("/{id}")
    @LogOperation(action = "UPDATE", module = "STATION", description = "更新监测站点")
    public Result<MonitorStation> update(@PathVariable Long id,
                                         @Valid @RequestBody StationRequest request) {
        if (stationService.getById(id) == null) throw ApiException.notFound("站点不存在");
        MonitorStation station = apply(new MonitorStation(), request);
        station.setId(id);
        stationService.updateById(station);
        return Result.success("更新成功", stationService.getById(id));
    }

    @DeleteMapping("/{id}")
    @LogOperation(action = "DELETE", module = "STATION", description = "删除监测站点")
    public Result<Void> delete(@PathVariable Long id) {
        if (!stationService.removeById(id)) throw ApiException.notFound("站点不存在");
        return Result.success("删除成功", null);
    }

    private MonitorStation apply(MonitorStation station, StationRequest request) {
        station.setName(request.getName().trim());
        station.setStationType(request.getStationType());
        station.setLat(request.getLat());
        station.setLng(request.getLng());
        station.setAddress(request.getAddress());
        station.setArea(request.getArea());
        station.setStatus(request.getStatus());
        station.setInstallDate(request.getInstallDate());
        station.setDescription(request.getDescription());
        return station;
    }

    private PumpStationView toPumpView(EmergencyResource resource) {
        String id = "P%03d".formatted(resource.getId());
        String amount = resource.getQuantity() == null ? "" : " (" + resource.getQuantity()
                + (resource.getUnit() == null ? "" : resource.getUnit()) + ")";
        String vehicle = resource.getDescription() == null || resource.getDescription().isBlank()
                ? resource.getName() + amount : resource.getDescription();
        String contact = (resource.getContactPerson() == null ? "" : resource.getContactPerson())
                + (resource.getContactPhone() == null ? "" : " (" + resource.getContactPhone() + ")");
        String status = "AVAILABLE".equals(resource.getStatus()) ? "空闲" : "已派发";
        return new PumpStationView(id, resource.getId(), resource.getName(), "pump", resource.getLng(), resource.getLat(),
                resource.getAddress(), vehicle, contact.trim(), status);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
