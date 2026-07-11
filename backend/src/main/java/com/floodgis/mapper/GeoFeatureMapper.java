package com.floodgis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.floodgis.entity.GeoFeature;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GeoFeatureMapper extends BaseMapper<GeoFeature> {

    @Select({
            "<script>",
            "SELECT COALESCE(BOOL_AND(ST_IsValid(",
            "    ST_SetSRID(ST_GeomFromGeoJSON(candidate.geometry_json), 4326)",
            ")), TRUE)",
            "FROM (VALUES",
            "<foreach collection='geometryJsonList' item='geometryJson' separator=','>",
            "    (CAST(#{geometryJson} AS text))",
            "</foreach>",
            ") AS candidate(geometry_json)",
            "</script>"
    })
    Boolean areGeometriesValid(@Param("geometryJsonList") List<String> geometryJsonList);

    @Insert("""
            INSERT INTO geo_feature (layer_id, name, geom, properties, created_at)
            VALUES (
                #{layerId},
                #{name},
                ST_SetSRID(ST_GeomFromGeoJSON(#{geomJson}), 4326),
                CAST(#{properties} AS jsonb),
                CURRENT_TIMESTAMP
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertFeature(GeoFeature feature);

    @Select("""
            SELECT gf.id,
                   gf.layer_id,
                   gf.name,
                   CAST(gf.properties AS text) AS properties,
                   gf.created_at,
                   ST_AsGeoJSON(gf.geom, 15, 0) AS geom_json
            FROM geo_feature gf
            WHERE gf.layer_id = #{layerId}
            ORDER BY gf.id
            """)
    List<GeoFeature> findByLayerId(@Param("layerId") Long layerId);
}
