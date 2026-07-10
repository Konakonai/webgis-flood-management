package com.floodgis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.floodgis.entity.WarningRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.time.LocalDateTime;

@Mapper
public interface WarningRecordMapper extends BaseMapper<WarningRecord> {

    @Select("SELECT * FROM warning_record WHERE id = #{id} FOR UPDATE")
    WarningRecord lockById(@Param("id") Long id);

    /** Serializes automatic warning creation per station across app instances. */
    @Select("SELECT id FROM monitor_station WHERE id = #{stationId} FOR UPDATE")
    Long lockStationForWarning(@Param("stationId") Long stationId);

    @Select("SELECT ST_IsValid(ST_SetSRID(ST_GeomFromGeoJSON(#{geoJson}), 4326))")
    Boolean isValidAffectedArea(@Param("geoJson") String geoJson);

    /**
     * 查询指定站点最近30分钟内的预警记录数
     */
    @Select("SELECT COUNT(*) FROM warning_record WHERE station_id = #{stationId} " +
            "AND created_at > NOW() - INTERVAL '30 minutes'")
    int countRecentWarnings(@Param("stationId") Long stationId);

    /**
     * 查询指定时间范围内的预警记录
     */
    @Select("SELECT * FROM warning_record WHERE created_at BETWEEN #{start} AND #{end} ORDER BY created_at DESC")
    List<WarningRecord> findByTimeRange(@Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);

    /**
     * 查询指定状态的预警数量
     */
    @Select("SELECT status, COUNT(*) as cnt FROM warning_record GROUP BY status")
    List<java.util.Map<String, Object>> countByStatus();
}
