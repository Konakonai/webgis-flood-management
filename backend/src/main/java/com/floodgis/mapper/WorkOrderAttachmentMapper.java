package com.floodgis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.floodgis.entity.WorkOrderAttachment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WorkOrderAttachmentMapper extends BaseMapper<WorkOrderAttachment> {
    @Select("SELECT * FROM work_order_attachment WHERE work_order_id = #{workOrderId} ORDER BY created_at")
    List<WorkOrderAttachment> findByWorkOrderId(Long workOrderId);

    @Select("SELECT COUNT(*) FROM work_order_attachment WHERE work_order_id = #{workOrderId}")
    int countByWorkOrderId(Long workOrderId);
}
