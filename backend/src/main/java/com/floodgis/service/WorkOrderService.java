package com.floodgis.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.floodgis.entity.WorkOrder;
import com.floodgis.entity.WorkOrderAttachment;
import com.floodgis.dto.DispatchRequest;
import com.floodgis.dto.PublicReportRequest;
import com.floodgis.dto.WorkOrderCreateRequest;
import com.floodgis.dto.WorkOrderStatusRequest;
import com.floodgis.dto.WorkOrderUpdateRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface WorkOrderService extends IService<WorkOrder> {
    WorkOrder createOrder(WorkOrderCreateRequest request, String actor);
    WorkOrder createPublicReport(PublicReportRequest request);
    WorkOrder updateOrder(Long id, WorkOrderUpdateRequest request);
    WorkOrder transition(Long id, WorkOrderStatusRequest request, String actor);
    WorkOrder dispatch(Long id, DispatchRequest request, String actor);
    void deleteOrder(Long id);
    Map<String, Object> track(String trackingCode);
    List<WorkOrder> nearby(double lng, double lat, int radiusMeters);
    WorkOrderAttachment addImage(String trackingCode, MultipartFile image);
}
