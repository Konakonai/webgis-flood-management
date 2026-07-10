package com.floodgis.config;

import com.floodgis.mapper.SysUserMapper;
import com.floodgis.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class WebSocketConfigTest {
    private ChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        WebSocketConfig config = new WebSocketConfig(
                mock(JwtUtils.class), mock(SysUserMapper.class));
        interceptor = config.clientInboundInterceptor();
    }

    @Test
    void viewerCannotSubscribeToWorkOrders() {
        Message<byte[]> subscribe = stompMessage(
                StompCommand.SUBSCRIBE, "/topic/work-orders", "ROLE_VIEWER");

        assertThrows(MessagingException.class, () -> interceptor.preSend(subscribe, null));
    }

    @Test
    void operatorAndAdminCanSubscribeToWorkOrders() {
        Message<byte[]> operator = stompMessage(
                StompCommand.SUBSCRIBE, "/topic/work-orders", "ROLE_OPERATOR");
        Message<byte[]> admin = stompMessage(
                StompCommand.SUBSCRIBE, "/topic/work-orders", "ROLE_ADMIN");

        assertDoesNotThrow(() -> interceptor.preSend(operator, null));
        assertDoesNotThrow(() -> interceptor.preSend(admin, null));
    }

    @Test
    void authenticatedClientStillCannotSendBusinessMessages() {
        Message<byte[]> send = stompMessage(StompCommand.SEND, "/app/anything", "ROLE_ADMIN");

        assertThrows(MessagingException.class, () -> interceptor.preSend(send, null));
    }

    private Message<byte[]> stompMessage(StompCommand command, String destination, String role) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                "user", "", List.of(new SimpleGrantedAuthority(role))));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
