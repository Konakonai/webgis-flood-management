package com.floodgis.config;

import com.floodgis.entity.SysUser;
import com.floodgis.mapper.SysUserMapper;
import com.floodgis.security.JwtUserDetails;
import com.floodgis.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtUtils jwtUtils;
    private final SysUserMapper sysUserMapper;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toArray(String[]::new);
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(origins)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(clientInboundInterceptor());
    }

    ChannelInterceptor clientInboundInterceptor() {
        return new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                        message, StompHeaderAccessor.class);
                if (accessor == null) return message;
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    authenticate(accessor);
                } else if (StompCommand.SEND.equals(accessor.getCommand())) {
                    // The current system is server-push only. Letting clients SEND to the
                    // simple-broker prefixes would allow them to forge operational events.
                    throw new MessagingException("WebSocket 不接受客户端业务消息");
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())
                        && accessor.getUser() == null) {
                    throw new MessagingException("WebSocket 未认证");
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())
                        && "/topic/work-orders".equals(accessor.getDestination())
                        && !canSubscribeToWorkOrders(accessor)) {
                    throw new MessagingException("工单主题仅允许管理员或操作员订阅");
                }
                return message;
            }
        };
    }

    private boolean canSubscribeToWorkOrders(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof Authentication authentication)) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())
                        || "ROLE_OPERATOR".equals(authority.getAuthority()));
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String bearer = accessor.getFirstNativeHeader("Authorization");
        if (!StringUtils.hasText(bearer) || !bearer.startsWith("Bearer ")) {
            throw new MessagingException("缺少 WebSocket Authorization 头");
        }

        String token = bearer.substring(7);
        if (!jwtUtils.validateToken(token)) {
            throw new MessagingException("WebSocket Token 无效或已过期");
        }

        Long userId = jwtUtils.getUserIdFromToken(token);
        String username = jwtUtils.getUsernameFromToken(token);
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled()) || !username.equals(user.getUsername())) {
            throw new MessagingException("WebSocket 用户已失效");
        }

        List<SimpleGrantedAuthority> authorities = sysUserMapper.findRolesByUserId(userId).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        JwtUserDetails details = new JwtUserDetails(userId, username, "", authorities);
        accessor.setUser(new UsernamePasswordAuthenticationToken(details, null, authorities));
    }
}
