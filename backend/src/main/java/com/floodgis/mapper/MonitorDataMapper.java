package com.floodgis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.floodgis.entity.MonitorData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

@Mapper
public interface MonitorDataMapper extends BaseMapper<MonitorData> {

    /**
     * 查询每个站点最新的监测数据
     */
    @Select("SELECT DISTINCT ON (station_id) * FROM monitor_data ORDER BY station_id, recorded_at DESC")
    List<MonitorData> findLatestByStations();

    /**
     * 查询指定站点的历史数据
     */
    @Select("SELECT * FROM monitor_data WHERE station_id = #{stationId} " +
            "AND data_type = #{dataType} AND recorded_at >= NOW() - make_interval(hours => #{hours}) " +
            "ORDER BY recorded_at DESC")
    List<MonitorData> findHistory(@Param("stationId") Long stationId,
                                   @Param("dataType") String dataType,
                                   @Param("hours") Integer hours);

    /**
     * 查询指定时间范围内的监测数据
     */
    @Select("SELECT * FROM monitor_data WHERE recorded_at BETWEEN #{start} AND #{end} ORDER BY recorded_at")
    List<MonitorData> findByTimeRange(@Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end);

    @Select("SELECT station_id, data_type, COUNT(*) AS sample_count, " +
            "ROUND(AVG(value)::numeric, 2) AS avg_value, " +
            "MIN(value) AS min_value, MAX(value) AS max_value " +
            "FROM monitor_data WHERE recorded_at >= NOW() - make_interval(hours => #{hours}) " +
            "GROUP BY station_id, data_type ORDER BY station_id, data_type")
    List<Map<String, Object>> summarize(@Param("hours") Integer hours);
}
