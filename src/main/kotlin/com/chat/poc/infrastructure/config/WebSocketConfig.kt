package com.chat.poc.infrastructure.config

import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(private val handshakeInterceptor: WebSocketHandshakeInterceptor) :
        WebSocketMessageBrokerConfigurer {

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        // 클라이언트가 구독할 수 있는 주제 prefix
        registry.enableSimpleBroker("/topic", "/queue")
        // 클라이언트가 메시지를 보낼 때 사용하는 prefix
        registry.setApplicationDestinationPrefixes("/app")
        // 특정 사용자에게 메시지를 보낼 때 사용하는 prefix
        registry.setUserDestinationPrefix("/user")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        // WebSocket 연결 엔드포인트 (SockJS)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(handshakeInterceptor)
                .withSockJS()

        // SockJS 없는 순수 WebSocket 엔드포인트
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(handshakeInterceptor)
    }
}
