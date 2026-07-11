package com.floodgis.mapper;

import com.floodgis.dto.SpatialFacilityRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SpatialFacilityMapper {

    @Select("""
            SELECT ST_IsValid(
                ST_SetSRID(ST_GeomFromGeoJSON(#{bufferGeoJson}), 4326)
            )
            """)
    Boolean isValidBuffer(@Param("bufferGeoJson") String bufferGeoJson);

    /**
     * 所有外部输入都通过 MyBatis #{} 绑定，没有用 ${} 拼接 SQL。
     */
    @Select({
            "<script>",
            "SELECT sf.id, sf.name,",
            "       sf.facility_type AS facility_type,",
            "       sf.type_name AS type_name,",
            "       sf.status, sf.status_name AS status_name,",
            "       sf.manager, sf.phone, sf.address,",
            "       sf.water_depth AS water_depth, sf.capacity,",
            "       ST_AsGeoJSON(sf.geom, 15, 0) AS geom_json",
            "FROM spatial_facility sf",
            "WHERE (#{name} = '' OR POSITION(#{name} IN sf.name) &gt; 0)",
            "  AND (#{type} = 'all' OR sf.facility_type = #{type})",
            "  AND GeometryType(sf.geom) = 'POINT'",
            "<if test='bufferGeoJson != null'>",
            "  AND ST_Intersects(",
            "      sf.geom,",
            "      ST_SetSRID(ST_GeomFromGeoJSON(#{bufferGeoJson}), 4326)",
            "  )",
            "</if>",
            "ORDER BY sf.id",
            "</script>"
    })
    List<SpatialFacilityRecord> search(
            @Param("name") String name,
            @Param("type") String type,
            @Param("bufferGeoJson") String bufferGeoJson);
}
