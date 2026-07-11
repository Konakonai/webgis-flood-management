package com.floodgis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.floodgis.entity.EmergencyResource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EmergencyResourceMapper extends BaseMapper<EmergencyResource> {
    @Select("SELECT * FROM emergency_resource WHERE id = #{id} FOR UPDATE")
    EmergencyResource lockById(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM work_order " +
            "WHERE assigned_resource_id = #{resourceId} AND status = 'PROCESSING'")
    long countProcessingAssignments(@Param("resourceId") Long resourceId);
}
