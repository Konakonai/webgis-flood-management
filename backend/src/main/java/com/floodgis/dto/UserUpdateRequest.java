package com.floodgis.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserUpdateRequest {
    @Size(max = 50, message = "姓名不能超过50个字符")
    private String realName;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱不能超过100个字符")
    private String email;

    @Pattern(regexp = "^$|^[0-9+() -]{6,20}$", message = "手机号格式不正确")
    private String phone;

    @Size(min = 8, max = 72, message = "密码长度必须为8到72个字符")
    private String password;
}
