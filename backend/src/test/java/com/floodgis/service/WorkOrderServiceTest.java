package com.floodgis.service;

import com.floodgis.config.ApiException;
import com.floodgis.dto.DispatchRequest;
import com.floodgis.dto.PublicReportRequest;
import com.floodgis.dto.WorkOrderStatusRequest;
import com.floodgis.entity.EmergencyResource;
import com.floodgis.entity.WorkOrder;
import com.floodgis.entity.WorkOrderAttachment;
import com.floodgis.mapper.EmergencyResourceMapper;
import com.floodgis.mapper.WorkOrderAttachmentMapper;
import com.floodgis.mapper.WorkOrderMapper;
import com.floodgis.mapper.WorkOrderStatusHistoryMapper;
import com.floodgis.service.impl.WorkOrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkOrderServiceTest {
    private WorkOrderMapper orderMapper;
    private EmergencyResourceMapper resourceMapper;
    private WorkOrderStatusHistoryMapper historyMapper;
    private WorkOrderAttachmentMapper attachmentMapper;
    private AttachmentStorageService storageService;
    private WorkOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        orderMapper = mock(WorkOrderMapper.class);
        resourceMapper = mock(EmergencyResourceMapper.class);
        historyMapper = mock(WorkOrderStatusHistoryMapper.class);
        attachmentMapper = mock(WorkOrderAttachmentMapper.class);
        storageService = mock(AttachmentStorageService.class);
        service = new WorkOrderServiceImpl(
                resourceMapper,
                historyMapper,
                attachmentMapper,
                storageService,
                mock(SimpMessagingTemplate.class));
        ReflectionTestUtils.setField(service, "baseMapper", orderMapper);
        when(orderMapper.insert(any())).thenAnswer(invocation -> {
            WorkOrder order = invocation.getArgument(0);
            order.setId(10L);
            return 1;
        });
        when(attachmentMapper.insert(any())).thenReturn(1);
    }

    @Test
    void publicReportGetsTrackingCodeAndPriority() {
        PublicReportRequest request = new PublicReportRequest();
        request.setLng(117.18);
        request.setLat(34.26);
        request.setDepth(55.0);
        request.setDescription("桥下积水");

        WorkOrder order = service.createPublicReport(request);

        assertEquals("REPORT", order.getType());
        assertEquals("PENDING", order.getStatus());
        assertEquals("URGENT", order.getPriority());
        assertTrue(order.getTrackingCode().matches("FR-[A-Z0-9]{12}"));
        verify(historyMapper).insert(any());
    }

    @Test
    void dispatchLocksAndClaimsAvailableResource() {
        WorkOrder order = order(1L, "PENDING");
        EmergencyResource resource = resource(2L, "AVAILABLE", "PUMP_TRUCK");
        when(orderMapper.lockById(1L)).thenReturn(order);
        when(resourceMapper.lockById(2L)).thenReturn(resource);
        DispatchRequest request = new DispatchRequest();
        request.setResourceId(2L);
        request.setHandlerName("张队长");

        WorkOrder result = service.dispatch(1L, request, "operator");

        assertEquals("PROCESSING", result.getStatus());
        assertEquals(2L, result.getAssignedResourceId());
        assertEquals("DISPATCHED", resource.getStatus());
        verify(resourceMapper).updateById(resource);
        verify(orderMapper).updateById(order);
    }

    @Test
    void dispatchRejectsAlreadyClaimedResource() {
        when(orderMapper.lockById(1L)).thenReturn(order(1L, "PENDING"));
        when(resourceMapper.lockById(2L)).thenReturn(resource(2L, "DISPATCHED", "PUMP_TRUCK"));
        DispatchRequest request = new DispatchRequest();
        request.setResourceId(2L);

        ApiException error = assertThrows(ApiException.class,
                () -> service.dispatch(1L, request, "operator"));
        assertEquals(409, error.getStatus().value());
        verify(orderMapper, never()).updateById(any());
    }

    @Test
    void markArrivedRecordsTimestampForProcessingOrder() {
        WorkOrder order = order(1L, "PROCESSING");
        when(orderMapper.lockById(1L)).thenReturn(order);

        WorkOrder result = service.markArrived(1L, "operator");

        assertNotNull(result.getArrivedAt());
        verify(orderMapper).updateById(order);
        verify(historyMapper).insert(argThat(history ->
                "应急资源抵达现场".equals(history.getNote())));
    }

    @Test
    void markArrivedRejectsPendingOrder() {
        when(orderMapper.lockById(1L)).thenReturn(order(1L, "PENDING"));

        ApiException error = assertThrows(ApiException.class,
                () -> service.markArrived(1L, "operator"));

        assertEquals(409, error.getStatus().value());
        verify(orderMapper, never()).updateById(any());
    }

    @Test
    void stateMachineRejectsSkippingProcessing() {
        when(orderMapper.lockById(1L)).thenReturn(order(1L, "PENDING"));
        WorkOrderStatusRequest request = new WorkOrderStatusRequest();
        request.setStatus("COMPLETED");

        ApiException error = assertThrows(ApiException.class,
                () -> service.transition(1L, request, "operator"));
        assertEquals(409, error.getStatus().value());
    }

    @Test
    void completingOrderReleasesAssignedResource() {
        WorkOrder order = order(1L, "PROCESSING");
        order.setAssignedResourceId(2L);
        EmergencyResource resource = resource(2L, "DISPATCHED", "PUMP_TRUCK");
        when(orderMapper.lockById(1L)).thenReturn(order);
        when(resourceMapper.lockById(2L)).thenReturn(resource);
        WorkOrderStatusRequest request = new WorkOrderStatusRequest();
        request.setStatus("COMPLETED");

        WorkOrder result = service.transition(1L, request, "operator");

        assertEquals("COMPLETED", result.getStatus());
        assertNotNull(result.getCompletedAt());
        assertEquals("AVAILABLE", resource.getStatus());
        verify(resourceMapper).updateById(resource);
    }

    @Test
    void deleteLocksOrderAndRejectsProcessingState() {
        when(orderMapper.lockById(9L)).thenReturn(order(9L, "PROCESSING"));

        ApiException error = assertThrows(ApiException.class, () -> service.deleteOrder(9L));

        assertEquals(409, error.getStatus().value());
        verify(orderMapper).lockById(9L);
        verify(orderMapper, never()).deleteById(9L);
    }

    @Test
    void deletingPendingOrderCleansAttachmentAfterDatabaseDelete() {
        WorkOrderAttachment attachment = new WorkOrderAttachment();
        attachment.setWorkOrderId(8L);
        attachment.setOriginalName("report.png");
        attachment.setStoredName("stored.png");
        attachment.setContentType("image/png");
        attachment.setFileSize(68L);
        attachment.setRelativePath("2026/07/stored.png");
        when(orderMapper.lockById(8L)).thenReturn(order(8L, "PENDING"));
        when(attachmentMapper.findByWorkOrderId(8L)).thenReturn(java.util.List.of(attachment));

        service.deleteOrder(8L);

        verify(orderMapper).deleteById(8L);
        verify(storageService).delete(any(AttachmentStorageService.StoredFile.class));
    }

    @Test
    void anonymousImageUploadLocksReportAndPersistsBelowLimit() {
        WorkOrder order = reportOrder(1L, "PENDING");
        MultipartFile image = mock(MultipartFile.class);
        AttachmentStorageService.StoredFile stored = storedFile();
        when(orderMapper.lockByTrackingCode("FR-ABC123")).thenReturn(order);
        when(attachmentMapper.countByWorkOrderId(1L)).thenReturn(4);
        when(storageService.store(image)).thenReturn(stored);

        WorkOrderAttachment attachment = service.addImage("FR-ABC123", image);

        assertEquals(1L, attachment.getWorkOrderId());
        assertEquals(stored.relativePath(), attachment.getRelativePath());
        verify(orderMapper).lockByTrackingCode("FR-ABC123");
        verify(attachmentMapper).insert(attachment);
    }

    @Test
    void anonymousImageUploadRejectsNonReportAndClosedReport() {
        WorkOrder internal = order(1L, "PENDING");
        internal.setType("DISPATCH");
        when(orderMapper.lockByTrackingCode("FR-INTERNAL")).thenReturn(internal);

        ApiException wrongType = assertThrows(ApiException.class,
                () -> service.addImage("FR-INTERNAL", mock(MultipartFile.class)));
        assertEquals(409, wrongType.getStatus().value());

        when(orderMapper.lockByTrackingCode("FR-CLOSED")).thenReturn(reportOrder(2L, "COMPLETED"));
        ApiException closed = assertThrows(ApiException.class,
                () -> service.addImage("FR-CLOSED", mock(MultipartFile.class)));
        assertEquals(409, closed.getStatus().value());
        verify(storageService, never()).store(any());
    }

    @Test
    void anonymousImageUploadRejectsSixthImageBeforeWritingFile() {
        when(orderMapper.lockByTrackingCode("FR-LIMIT5"))
                .thenReturn(reportOrder(1L, "PROCESSING"));
        when(attachmentMapper.countByWorkOrderId(1L)).thenReturn(5);

        ApiException error = assertThrows(ApiException.class,
                () -> service.addImage("FR-LIMIT5", mock(MultipartFile.class)));

        assertEquals(409, error.getStatus().value());
        verify(storageService, never()).store(any());
    }

    @Test
    void attachmentInsertFailureDeletesStoredFile() {
        MultipartFile image = mock(MultipartFile.class);
        AttachmentStorageService.StoredFile stored = storedFile();
        when(orderMapper.lockByTrackingCode("FR-DBFAIL")).thenReturn(reportOrder(1L, "PENDING"));
        when(storageService.store(image)).thenReturn(stored);
        when(attachmentMapper.insert(any())).thenThrow(new IllegalStateException("db failed"));

        assertThrows(IllegalStateException.class, () -> service.addImage("FR-DBFAIL", image));

        verify(storageService).delete(stored);
    }

    @Test
    void laterTransactionRollbackDeletesStoredFile() {
        MultipartFile image = mock(MultipartFile.class);
        AttachmentStorageService.StoredFile stored = storedFile();
        when(orderMapper.lockByTrackingCode("FR-ROLLBACK")).thenReturn(reportOrder(1L, "PENDING"));
        when(storageService.store(image)).thenReturn(stored);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.addImage("FR-ROLLBACK", image);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(storageService).delete(stored);
    }

    private WorkOrder order(Long id, String status) {
        WorkOrder order = new WorkOrder();
        order.setId(id);
        order.setStatus(status);
        return order;
    }

    private EmergencyResource resource(Long id, String status, String type) {
        EmergencyResource resource = new EmergencyResource();
        resource.setId(id);
        resource.setName("资源" + id);
        resource.setStatus(status);
        resource.setResourceType(type);
        return resource;
    }

    private WorkOrder reportOrder(Long id, String status) {
        WorkOrder order = order(id, status);
        order.setType("REPORT");
        return order;
    }

    private AttachmentStorageService.StoredFile storedFile() {
        return new AttachmentStorageService.StoredFile(
                "report.png", "stored.png", "image/png", 68, "2026/07/stored.png");
    }
}
