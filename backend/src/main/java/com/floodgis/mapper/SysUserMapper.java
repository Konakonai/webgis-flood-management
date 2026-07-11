package com.floodgis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.floodgis.entity.SysUser;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT id FROM sys_role WHERE role_code = #{roleCode} FOR UPDATE")
    Long lockRoleForUserStatus(@Param("roleCode") String roleCode);

    @Select("SELECT * FROM sys_user WHERE id = #{userId} FOR UPDATE")
    SysUser lockById(@Param("userId") Long userId);

    @Select("SELECT r.role_code FROM sys_role r " +
            "INNER JOIN sys_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId}")
    List<String> findRolesByUserId(@Param("userId") Long userId);

    @Insert("INSERT INTO sys_user_role (user_id, role_id) VALUES (#{userId}, #{roleId})")
    void insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Select("SELECT id FROM sys_role WHERE role_code = #{roleCode}")
    Long findRoleIdByCode(@Param("roleCode") String roleCode);

    @Select("SELECT COUNT(*) FROM sys_user u " +
            "JOIN sys_user_role ur ON ur.user_id = u.id " +
            "JOIN sys_role r ON r.id = ur.role_id " +
            "WHERE u.enabled = TRUE AND r.role_code = #{roleCode}")
    long countEnabledUsersByRole(@Param("roleCode") String roleCode);

    @Select("SELECT EXISTS (SELECT 1 FROM sys_user_role ur " +
            "JOIN sys_role r ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.role_code = #{roleCode})")
    boolean hasRole(@Param("userId") Long userId, @Param("roleCode") String roleCode);
}
