package com.chat.poc.infrastructure.config

import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.stereotype.Component
import org.springframework.web.socket.messaging.SessionConnectEvent
import org.springframework.web.socket.messaging.SessionDisconnectEvent
import org.springframework.web.socket.messaging.SessionSubscribeEvent

/** WebSocket 세션 이벤트 리스너 STOMP 연결, 구독, 해제 이벤트를 로깅합니다. */
@Component
class WebSocketEventListener {

    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun handleSessionConnected(event: SessionConnectEvent) {
        val headerAccessor = StompHeaderAccessor.wrap(event.message)
        val sessionId = headerAccessor.sessionId
        val userId = headerAccessor.sessionAttributes?.get("userId")
        val userType = headerAccessor.sessionAttributes?.get("userType")

        log.info(
                "[WS ↔] Session connected - sessionId: $sessionId, userId: $userId, userType: $userType"
        )
    }

    @EventListener
    fun handleSessionSubscribe(event: SessionSubscribeEvent) {
        val headerAccessor = StompHeaderAccessor.wrap(event.message)
        val sessionId = headerAccessor.sessionId
        val destination = headerAccessor.destination

        log.info("[WS 📡] Subscribed - sessionId: $sessionId, destination: $destination")
    }

    @EventListener
    fun handleSessionDisconnected(event: SessionDisconnectEvent) {
        val headerAccessor = StompHeaderAccessor.wrap(event.message)
        val sessionId = headerAccessor.sessionId
        val userId = headerAccessor.sessionAttributes?.get("userId")

        log.info("[WS ↙] Session disconnected - sessionId: $sessionId, userId: $userId")
    }
}
