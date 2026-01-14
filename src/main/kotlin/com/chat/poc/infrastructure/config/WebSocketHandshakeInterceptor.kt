package com.chat.poc.infrastructure.config

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor

/** HTTP 세션을 WebSocket 세션에 복사하는 인터셉터 */
@Configuration
class WebSocketHandshakeInterceptor : HandshakeInterceptor {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun beforeHandshake(
            request: ServerHttpRequest,
            response: ServerHttpResponse,
            wsHandler: WebSocketHandler,
            attributes: MutableMap<String, Any>
    ): Boolean {
        if (request is ServletServerHttpRequest) {
            val session = request.servletRequest.session

            // HTTP 세션의 인증 정보를 WebSocket 세션으로 복사
            val userId = session.getAttribute("userId")
            val userType = session.getAttribute("userType")

            userId?.let { attributes["userId"] = it }
            userType?.let { attributes["userType"] = it }

            log.info("[WS ↗] Handshake started - userId: $userId, userType: $userType")
        }
        return true
    }

    override fun afterHandshake(
            request: ServerHttpRequest,
            response: ServerHttpResponse,
            wsHandler: WebSocketHandler,
            exception: Exception?
    ) {
        if (exception != null) {
            log.warn("[WS ✗] Handshake failed: ${exception.message}")
        } else {
            log.info("[WS ✓] Handshake completed")
        }
    }
}
