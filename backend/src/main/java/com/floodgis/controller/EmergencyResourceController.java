package com.floodgis.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.floodgis.aspect.LogOperation;
import com.floodgis.config.ApiException;
import com.floodgis.dto.ResourcePointView;
import com.floodgis.dto.ResourceRequest;
import com.floodgis.dto.Result;
import com.floodgis.entity.EmergencyResource;
import com.floodgis.service.EmergencyResourceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
@Validated
public class EmergencyResourceController {

    private final EmergencyResourceService resourceService;

    /** Fixed public map contract used by the existing frontend. */
    @GetMapping(params = {"!page", "!size", "!name", "!resourceType", "!status"})
    public List<ResourcePointView> publicList() {
        return resourceService.list(new LambdaQueryWrapper<EmergencyResource>()
                        .ne(EmergencyResource::getResourceType, "PUMP_TRUCK")
                        .orderByAsc(EmergencyResource::getId))
                .stream()
                .map(this::toPublicView)
                .toList();
    }

    @GetMapping(params = "page")
    public Result<Page<EmergencyResource>> list(
            @RequestParam @Min(1) Integer page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<EmergencyResource> wrapper = new LambdaQueryWrapper<>();
        if (hasText(name)) wrapper.like(EmergencyResource::getName, name.trim());
        if (hasText(resourceType)) wrapper.eq(EmergencyResource::getResourceType, resourceType);
        if (hasText(status)) wrapper.eq(EmergencyResource::getStatus, status);
        wrapper.orderByDesc(EmergencyResource::getCreatedAt);
        return Result.success(resourceService.page(new Page<>(page, size), wrapper));
    }

    @GetMapping("/{id}")
    public Result<EmergencyResource> getById(@PathVariable Long id) {
        EmergencyResource resource = resourceService.getById(id);
        if (resource == null) throw ApiException.notFound("资源不存在");
        return Result.success(resource);
    }

    @PostMapping
    @LogOperation(action = "CREATE", module = "RESOURCE", description = "新增应急资源")
    public Result<EmergencyResource> create(@Valid @RequestBody ResourceRequest request) {
        EmergencyResource resource = apply(new EmergencyResource(), request);
        resourceService.createManaged(resource);
        return Result.success("新增成功", resource);
    }

    @PutMapping("/{id}")
    @LogOperation(action = "UPDATE", module = "RESOURCE", description = "更新应急资源")
    public Result<EmergencyResource> update(@PathVariable Long id,
                                            @Valid @RequestBody ResourceRequest request) {
        EmergencyResource resource = apply(new EmergencyResource(), request);
        return Result.success("更新成功", resourceService.updateManaged(id, resource));
    }

    @DeleteMapping("/{id}")
    @LogOperation(action = "DELETE", module = "RESOURCE", description = "删除应急资源")
    public Result<Void> delete(@PathVariable Long id) {
        resourceService.deleteManaged(id);
        return Result.success("删除成功", null);
    }

    private EmergencyResource apply(EmergencyResource resource, ResourceRequest request) {
        resource.setName(request.getName().trim());
        resource.setResourceType(request.getResourceType());
        resource.setLat(request.getLat());
        resource.setLng(request.getLng());
        resource.setAddress(request.getAddress());
        resource.setArea(request.getArea());
        resource.setQuantity(request.getQuantity());
        resource.setUnit(request.getUnit());
        resource.setContactPerson(request.getContactPerson());
        resource.setContactPhone(request.getContactPhone());
        resource.setStatus(request.getStatus());
        resource.setDescription(request.getDescription());
        return resource;
    }

    private ResourcePointView toPublicView(EmergencyResource resource) {
        String id = "R%03d".formatted(resource.getId());
        String status = "DEPLETED".equals(resource.getStatus())
                || (resource.getQuantity() != null && resource.getQuantity() < 100) ? "紧张" : "充足";
        String inventory = resource.getQuantity() == null ? "" : resource.getQuantity() +
                (resource.getUnit() == null ? "" : resource.getUnit());
        String details = String.join("；", List.of(inventory,
                        resource.getDescription() == null ? "" : resource.getDescription()))
                .replaceAll("^；|；$", "");
        return new ResourcePointView(id, resource.getName(), "resource", resource.getLng(), resource.getLat(),
                resource.getAddress(), status, details);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
