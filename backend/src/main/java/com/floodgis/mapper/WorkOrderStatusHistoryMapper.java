package com.floodgis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.floodgis.entity.WorkOrderStatusHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WorkOrderStatusHistoryMapper extends BaseMapper<WorkOrderStatusHistory> {
    @Select("SELECT * FROM work_order_status_history WHERE work_order_id = #{workOrderId} ORDER BY created_at")
    List<WorkOrderStatusHistory> findByWorkOrderId(Long workOrderId);
}
