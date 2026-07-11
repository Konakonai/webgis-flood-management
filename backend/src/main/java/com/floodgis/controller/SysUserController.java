package com.floodgis.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.floodgis.dto.Result;
import com.floodgis.dto.UserUpdateRequest;
import com.floodgis.entity.SysUser;
import com.floodgis.config.ApiException;
import com.floodgis.aspect.LogOperation;
import com.floodgis.security.JwtUserDetails;
import com.floodgis.service.SysUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Validated
public class SysUserController {

    private final SysUserService userService;
    private final PasswordEncoder passwordEncoder;

    /**
     * 分页查询用户列表
     */
    @GetMapping
    public Result<Page<SysUser>> list(
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer size,
            @RequestParam(required = false) String keyword) {

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(SysUser::getUsername, keyword)
                   .or().like(SysUser::getRealName, keyword);
        }
        wrapper.orderByDesc(SysUser::getCreatedAt);

        Page<SysUser> result = userService.page(new Page<>(page, size), wrapper);
        // 清除密码
        result.getRecords().forEach(user -> user.setPassword(null));
        return Result.success(result);
    }

    /**
     * 获取用户详情
     */
    @GetMapping("/{id}")
    public Result<SysUser> getById(@PathVariable Long id) {
        SysUser user = userService.getById(id);
        if (user == null) throw ApiException.notFound("用户不存在");
        user.setPassword(null);
        return Result.success(user);
    }

    /**
     * 更新用户信息
     */
    @PutMapping("/{id}")
    @LogOperation(action = "UPDATE", module = "USER", description = "更新用户资料")
    public Result<SysUser> update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        SysUser user = userService.getById(id);
        if (user == null) {
            throw ApiException.notFound("用户不存在");
        }
        user.setRealName(request.getRealName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        userService.updateById(user);
        SysUser updated = userService.getById(id);
        updated.setPassword(null);
        return Result.success("更新成功", updated);
    }

    /**
     * 禁用/启用用户
     */
    @PutMapping("/{id}/status")
    @LogOperation(action = "UPDATE", module = "USER", description = "切换用户启用状态")
    public Result<Void> toggleStatus(@PathVariable Long id,
                                     @RequestParam Boolean enabled,
                                     @AuthenticationPrincipal JwtUserDetails currentUser) {
        userService.setEnabled(id, enabled, currentUser == null ? null : currentUser.getUserId());
        return Result.success(enabled ? "用户已启用" : "用户已禁用", null);
    }
}
