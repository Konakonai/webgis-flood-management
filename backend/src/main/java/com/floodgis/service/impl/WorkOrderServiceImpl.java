package com.floodgis.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.floodgis.config.ApiException;
import com.floodgis.dto.DispatchRequest;
import com.floodgis.dto.PublicReportRequest;
import com.floodgis.dto.WorkOrderCreateRequest;
import com.floodgis.dto.WorkOrderStatusRequest;
import com.floodgis.dto.WorkOrderUpdateRequest;
import com.floodgis.entity.EmergencyResource;
import com.floodgis.entity.WorkOrder;
import com.floodgis.entity.WorkOrderAttachment;
import com.floodgis.entity.WorkOrderStatusHistory;
import com.floodgis.mapper.EmergencyResourceMapper;
import com.floodgis.mapper.WorkOrderAttachmentMapper;
import com.floodgis.mapper.WorkOrderMapper;
import com.floodgis.mapper.WorkOrderStatusHistoryMapper;
import com.floodgis.service.AttachmentStorageService;
import com.floodgis.service.WorkOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkOrderServiceImpl extends ServiceImpl<WorkOrderMapper, WorkOrder>
        implements WorkOrderService {

    private static final int MAX_REPORT_IMAGES = 5;

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "PENDING", Set.of("PROCESSING", "REJECTED"),
            "PROCESSING", Set.of("COMPLETED", "REJECTED"),
            "COMPLETED", Set.of(),
            "REJECTED", Set.of());

    private final EmergencyResourceMapper resourceMapper;
    private final WorkOrderStatusHistoryMapper historyMapper;
    private final WorkOrderAttachmentMapper attachmentMapper;
    private final AttachmentStorageService storageService;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional
    public WorkOrder createOrder(WorkOrderCreateRequest request, String actor) {
        WorkOrder order = new WorkOrder();
        order.setType(request.getType());
        order.setTitle(request.getTitle().trim());
        order.setDescription(request.getDescription());
        order.setLat(request.getLat());
        order.setLng(request.getLng());
        order.setPriority(request.getPriority());
        order.setReporterName(request.getReporterName());
        order.setReporterPhone(request.getReporterPhone());
        order.setStatus("PENDING");
        order.setTrackingCode(newTrackingCode());
        save(order);
        recordHistory(order.getId(), null, "PENDING", actor, "创建工单");
        publishAfterCommit(order);
        return order;
    }

    @Override
    @Transactional
    public WorkOrder createPublicReport(PublicReportRequest request) {
        WorkOrder order = new WorkOrder();
        order.setType("REPORT");
        order.setTitle("公众积水上报");
        order.setDescription(request.getDescription());
        order.setLat(request.getLat());
        order.setLng(request.getLng());
        order.setWaterDepthCm(request.getDepth());
        order.setPriority(priorityForDepth(request.getDepth()));
        order.setReporterName(request.getReporterName());
        order.setReporterPhone(request.getReporterPhone());
        order.setStatus("PENDING");
        order.setTrackingCode(newTrackingCode());
        save(order);
        recordHistory(order.getId(), null, "PENDING", "PUBLIC", "公众移动端上报");

        if (request.getImage() != null && !request.getImage().isBlank()) {
            persistAttachment(order.getId(), storageService.storeDataUrl(request.getImage()));
        }
        publishAfterCommit(order);
        return order;
    }

    @Override
    @Transactional
    public WorkOrder updateOrder(Long id, WorkOrderUpdateRequest request) {
        WorkOrder order = baseMapper.lockById(id);
        if (order == null) throw ApiException.notFound("工单不存在");
        if (request.getTitle() != null) order.setTitle(request.getTitle().trim());
        if (request.getDescription() != null) order.setDescription(request.getDescription());
        if (request.getPriority() != null) order.setPriority(request.getPriority());
        if (request.getResult() != null) order.setResult(request.getResult());
        updateById(order);
        publishAfterCommit(order);
        return order;
    }

    @Override
    @Transactional
    public WorkOrder transition(Long id, WorkOrderStatusRequest request, String actor) {
        WorkOrder order = baseMapper.lockById(id);
        if (order == null) throw ApiException.notFound("工单不存在");
        String current = order.getStatus();
        String target = request.getStatus();
        if (!TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw ApiException.conflict("不允许从 " + current + " 迁移到 " + target);
        }
        applyStatus(order, target, request.getResult());
        updateById(order);
        if (("COMPLETED".equals(target) || "REJECTED".equals(target))
                && order.getAssignedResourceId() != null) {
            releaseResource(order.getAssignedResourceId());
        }
        recordHistory(id, current, target, actor, request.getNote());
        publishAfterCommit(order);
        return order;
    }

    @Override
    @Transactional
    public WorkOrder dispatch(Long id, DispatchRequest request, String actor) {
        WorkOrder order = baseMapper.lockById(id);
        if (order == null) throw ApiException.notFound("工单不存在");
        if (!"PENDING".equals(order.getStatus())) {
            throw ApiException.conflict("仅待派单工单可以派发");
        }
        EmergencyResource resource = resourceMapper.lockById(request.getResourceId());
        if (resource == null) throw ApiException.notFound("应急资源不存在");
        if (!"AVAILABLE".equals(resource.getStatus())) {
            throw ApiException.conflict("应急资源当前不可用");
        }
        if (!Set.of("PUMP_TRUCK", "TEAM").contains(resource.getResourceType())) {
            throw ApiException.badRequest("仅泵车或救援队可用于派单");
        }

        resource.setStatus("DISPATCHED");
        resourceMapper.updateById(resource);
        order.setAssignedResourceId(resource.getId());
        order.setHandlerId(request.getHandlerId());
        order.setHandlerName(request.getHandlerName());
        order.setRouteDistance(request.getRouteDistance());
        order.setRouteDuration(request.getRouteDuration());
        order.setStatus("PROCESSING");
        order.setDispatchedAt(LocalDateTime.now());
        order.setProcessedAt(order.getDispatchedAt());
        updateById(order);
        recordHistory(id, "PENDING", "PROCESSING", actor,
                "派发资源: " + resource.getName());
        publishAfterCommit(order);
        return order;
    }

    @Override
    @Transactional
    public void deleteOrder(Long id) {
        WorkOrder order = baseMapper.lockById(id);
        if (order == null) throw ApiException.notFound("工单不存在");
        if (!Set.of("PENDING", "REJECTED").contains(order.getStatus())) {
            throw ApiException.conflict("处置中的工单不能删除");
        }
        List<WorkOrderAttachment> attachments = attachmentMapper.findByWorkOrderId(id);
        baseMapper.deleteById(id);
        deleteAttachmentsAfterCommit(attachments);
    }

    @Override
    public Map<String, Object> track(String trackingCode) {
        WorkOrder order = findByTrackingCode(trackingCode);
        Map<String, Object> result = new HashMap<>();
        result.put("id", order.getTrackingCode());
        result.put("trackingCode", order.getTrackingCode());
        result.put("status", order.getStatus());
        result.put("title", order.getTitle());
        result.put("description", order.getDescription());
        result.put("lng", order.getLng());
        result.put("lat", order.getLat());
        result.put("depth", order.getWaterDepthCm());
        result.put("createdAt", order.getCreatedAt());
        result.put("updatedAt", order.getUpdatedAt());
        result.put("result", order.getResult());
        result.put("history", historyMapper.findByWorkOrderId(order.getId()));
        result.put("images", attachmentMapper.findByWorkOrderId(order.getId()).stream()
                .map(file -> "/uploads/" + file.getRelativePath()).toList());
        return result;
    }

    @Override
    public List<WorkOrder> nearby(double lng, double lat, int radiusMeters) {
        return baseMapper.findNearbyUnresolved(lng, lat, radiusMeters);
    }

    @Override
    @Transactional
    public WorkOrderAttachment addImage(String trackingCode, MultipartFile image) {
        validateTrackingCode(trackingCode);
        WorkOrder order = baseMapper.lockByTrackingCode(trackingCode);
        if (order == null) throw ApiException.notFound("未找到对应上报记录");
        if (!"REPORT".equals(order.getType())) {
            throw ApiException.conflict("仅公众上报工单可以追加图片");
        }
        if (!Set.of("PENDING", "PROCESSING").contains(order.getStatus())) {
            throw ApiException.conflict("仅待处理或处理中的上报可以追加图片");
        }
        if (attachmentMapper.countByWorkOrderId(order.getId()) >= MAX_REPORT_IMAGES) {
            throw ApiException.conflict("每个上报最多上传 " + MAX_REPORT_IMAGES + " 张图片");
        }
        return persistAttachment(order.getId(), storageService.store(image));
    }

    private WorkOrder findByTrackingCode(String trackingCode) {
        validateTrackingCode(trackingCode);
        WorkOrder order = baseMapper.findByTrackingCode(trackingCode);
        if (order == null) throw ApiException.notFound("未找到对应上报记录");
        return order;
    }

    private void validateTrackingCode(String trackingCode) {
        if (trackingCode == null || !trackingCode.matches("[A-Z0-9-]{6,32}")) {
            throw ApiException.badRequest("追踪码格式不正确");
        }
    }

    private WorkOrderAttachment persistAttachment(Long workOrderId,
                                                  AttachmentStorageService.StoredFile stored) {
        registerRollbackCleanup(stored);
        try {
            WorkOrderAttachment attachment = new WorkOrderAttachment();
            attachment.setWorkOrderId(workOrderId);
            attachment.setOriginalName(stored.originalName());
            attachment.setStoredName(stored.storedName());
            attachment.setContentType(stored.contentType());
            attachment.setFileSize(stored.fileSize());
            attachment.setRelativePath(stored.relativePath());
            attachment.setCreatedAt(LocalDateTime.now());
            if (attachmentMapper.insert(attachment) != 1) {
                throw new IllegalStateException("附件记录写入失败");
            }
            return attachment;
        } catch (RuntimeException error) {
            storageService.delete(stored);
            throw error;
        }
    }

    private void registerRollbackCleanup(AttachmentStorageService.StoredFile stored) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    storageService.delete(stored);
                }
            }
        });
    }

    private void deleteAttachmentsAfterCommit(List<WorkOrderAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) return;
        Runnable cleanup = () -> attachments.forEach(file -> storageService.delete(
                new AttachmentStorageService.StoredFile(
                        file.getOriginalName(), file.getStoredName(), file.getContentType(),
                        file.getFileSize(), file.getRelativePath())));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { cleanup.run(); }
            });
        } else {
            cleanup.run();
        }
    }

    private void releaseResource(Long resourceId) {
        EmergencyResource resource = resourceMapper.lockById(resourceId);
        if (resource != null && "DISPATCHED".equals(resource.getStatus())) {
            resource.setStatus("AVAILABLE");
            resourceMapper.updateById(resource);
        }
    }

    private void applyStatus(WorkOrder order, String status, String result) {
        order.setStatus(status);
        if (result != null) order.setResult(result);
        LocalDateTime now = LocalDateTime.now();
        if ("PROCESSING".equals(status) && order.getProcessedAt() == null) order.setProcessedAt(now);
        if ("COMPLETED".equals(status)) {
            order.setCompletedAt(now);
            order.setProcessedAt(now);
        }
    }

    private void recordHistory(Long orderId, String from, String to, String actor, String note) {
        WorkOrderStatusHistory history = new WorkOrderStatusHistory();
        history.setWorkOrderId(orderId);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setOperatorName(actor == null || actor.isBlank() ? "SYSTEM" : actor);
        history.setNote(note);
        history.setCreatedAt(LocalDateTime.now());
        historyMapper.insert(history);
    }

    private void publishAfterCommit(WorkOrder order) {
        Runnable publish = () -> messagingTemplate.convertAndSend("/topic/work-orders", order);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { publish.run(); }
            });
        } else {
            publish.run();
        }
    }

    private String newTrackingCode() {
        return "FR-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase();
    }

    private String priorityForDepth(Double depth) {
        if (depth >= 50) return "URGENT";
        if (depth >= 30) return "HIGH";
        if (depth >= 10) return "NORMAL";
        return "LOW";
    }
}
