package com.floodgis.config;

import com.floodgis.controller.AuthController;
import com.floodgis.controller.WorkOrderController;
import com.floodgis.entity.SysUser;
import com.floodgis.mapper.SysUserMapper;
import com.floodgis.security.JwtAuthenticationFilter;
import com.floodgis.security.JwtUtils;
import com.floodgis.service.AuthService;
import com.floodgis.service.WorkOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AuthController.class, WorkOrderController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class SecurityConfigTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthService authService;

    @MockitoBean
    WorkOrderService workOrderService;

    @MockitoBean
    JwtUtils jwtUtils;

    @MockitoBean
    SysUserMapper sysUserMapper;

    @Test
    void anonymousRegistrationRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(validRegistration()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCannotRegisterOrReadWorkOrders() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(validRegistration()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/work-orders"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanRegister() throws Exception {
        when(authService.register(any())).thenReturn(new SysUser());

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(validRegistration()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operatorCanReadWorkOrders() throws Exception {
        mockMvc.perform(get("/api/work-orders"))
                .andExpect(status().isOk());
    }

    private String validRegistration() {
        return """
                {"username":"new-user","password":"password123","realName":"新用户"}
                """;
    }
}
