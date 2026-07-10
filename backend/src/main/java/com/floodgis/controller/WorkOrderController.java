package com.floodgis.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.floodgis.aspect.LogOperation;
import com.floodgis.config.ApiException;
import com.floodgis.dto.DispatchRequest;
import com.floodgis.dto.Result;
import com.floodgis.dto.WorkOrderCreateRequest;
import com.floodgis.dto.WorkOrderStatusRequest;
import com.floodgis.dto.WorkOrderUpdateRequest;
import com.floodgis.entity.WorkOrder;
import com.floodgis.security.JwtUserDetails;
import com.floodgis.service.WorkOrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/work-orders")
@RequiredArgsConstructor
@Validated
public class WorkOrderController {
    private final WorkOrderService workOrderService;

    @GetMapping
    public Result<Page<WorkOrder>> list(
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<>();
        if (hasText(type)) wrapper.eq(WorkOrder::getType, type);
        if (hasText(status)) wrapper.eq(WorkOrder::getStatus, status);
        if (hasText(keyword)) {
            String value = keyword.trim();
            wrapper.and(group -> group.like(WorkOrder::getTitle, value)
                    .or().like(WorkOrder::getDescription, value));
        }
        wrapper.orderByDesc(WorkOrder::getCreatedAt);
        return Result.success(workOrderService.page(new Page<>(page, size), wrapper));
    }

    @GetMapping("/{id}")
    public Result<WorkOrder> getById(@PathVariable Long id) {
        WorkOrder order = workOrderService.getById(id);
        if (order == null) throw ApiException.notFound("工单不存在");
        return Result.success(order);
    }

    @PostMapping
    @LogOperation(action = "CREATE", module = "WORK_ORDER", description = "创建工单")
    public Result<WorkOrder> create(@Valid @RequestBody WorkOrderCreateRequest request,
                                    @AuthenticationPrincipal JwtUserDetails user) {
        return Result.success("创建成功", workOrderService.createOrder(request, actor(user)));
    }

    /** Backwards-compatible status endpoint from the original API contract. */
    @PutMapping("/{id}/status")
    @LogOperation(action = "UPDATE", module = "WORK_ORDER", description = "更新工单状态")
    public Result<WorkOrder> updateStatus(@PathVariable Long id,
                                          @RequestParam String status,
                                          @RequestBody(required = false) WorkOrder updateInfo,
                                          @AuthenticationPrincipal JwtUserDetails user) {
        if (!Set.of("PROCESSING", "COMPLETED", "REJECTED").contains(status)) {
            throw ApiException.badRequest("工单状态不合法");
        }
        WorkOrderStatusRequest request = new WorkOrderStatusRequest();
        request.setStatus(status);
        if (updateInfo != null) {
            request.setResult(updateInfo.getResult());
            request.setNote(updateInfo.getDescription());
        }
        return Result.success("状态更新成功", workOrderService.transition(id, request, actor(user)));
    }

    @PatchMapping("/{id}/status")
    @LogOperation(action = "UPDATE", module = "WORK_ORDER", description = "迁移工单状态")
    public Result<WorkOrder> transition(@PathVariable Long id,
                                        @Valid @RequestBody WorkOrderStatusRequest request,
                                        @AuthenticationPrincipal JwtUserDetails user) {
        return Result.success("状态更新成功", workOrderService.transition(id, request, actor(user)));
    }

    @PostMapping("/{id}/dispatch")
    @LogOperation(action = "DISPATCH", module = "WORK_ORDER", description = "派发应急资源")
    public Result<WorkOrder> dispatch(@PathVariable Long id,
                                      @Valid @RequestBody DispatchRequest request,
                                      @AuthenticationPrincipal JwtUserDetails user) {
        return Result.success("派单成功", workOrderService.dispatch(id, request, actor(user)));
    }

    @PutMapping("/{id}")
    @LogOperation(action = "UPDATE", module = "WORK_ORDER", description = "更新工单资料")
    public Result<WorkOrder> update(@PathVariable Long id,
                                    @Valid @RequestBody WorkOrderUpdateRequest request) {
        return Result.success("更新成功", workOrderService.updateOrder(id, request));
    }

    @DeleteMapping("/{id}")
    @LogOperation(action = "DELETE", module = "WORK_ORDER", description = "删除工单")
    public Result<Void> delete(@PathVariable Long id) {
        workOrderService.deleteOrder(id);
        return Result.success("删除成功", null);
    }

    private String actor(JwtUserDetails user) {
        return user == null ? "SYSTEM" : user.getUsername();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
