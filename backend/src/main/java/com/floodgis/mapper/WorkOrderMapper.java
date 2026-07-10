package com.floodgis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.floodgis.entity.WorkOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WorkOrderMapper extends BaseMapper<WorkOrder> {
    @Select("SELECT * FROM work_order WHERE id = #{id} FOR UPDATE")
    WorkOrder lockById(Long id);

    @Select("SELECT * FROM work_order WHERE tracking_code = #{trackingCode}")
    WorkOrder findByTrackingCode(String trackingCode);

    @Select("SELECT * FROM work_order WHERE tracking_code = #{trackingCode} FOR UPDATE")
    WorkOrder lockByTrackingCode(String trackingCode);

    @Select("SELECT * FROM work_order WHERE type = 'REPORT' " +
            "AND status IN ('PENDING','PROCESSING') AND geom IS NOT NULL " +
            "AND ST_DWithin(geom::geography, " +
            "ST_SetSRID(ST_MakePoint(#{lng}, #{lat}), 4326)::geography, #{radiusMeters}) " +
            "ORDER BY geom <-> ST_SetSRID(ST_MakePoint(#{lng}, #{lat}), 4326) LIMIT 100")
    List<WorkOrder> findNearbyUnresolved(@Param("lng") double lng,
                                         @Param("lat") double lat,
                                         @Param("radiusMeters") int radiusMeters);
}
