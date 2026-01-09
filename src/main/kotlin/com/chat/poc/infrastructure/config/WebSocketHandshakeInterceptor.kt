package com.chat.poc.infrastructure.config

import org.springframework.context.annotation.Configuration
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor

/** HTTP 세션을 WebSocket 세션에 복사하는 인터셉터 */
@Configuration
class WebSocketHandshakeInterceptor : HandshakeInterceptor {

    override fun beforeHandshake(
            request: ServerHttpRequest,
            response: ServerHttpResponse,
            wsHandler: WebSocketHandler,
            attributes: MutableMap<String, Any>
    ): Boolean {
        if (request is ServletServerHttpRequest) {
            val session = request.servletRequest.session

            // HTTP 세션의 인증 정보를 WebSocket 세션으로 복사
            session.getAttribute("userId")?.let { attributes["userId"] = it }
            session.getAttribute("userType")?.let { attributes["userType"] = it }
        }
        return true
    }

    override fun afterHandshake(
            request: ServerHttpRequest,
            response: ServerHttpResponse,
            wsHandler: WebSocketHandler,
            exception: Exception?
    ) {
        // Nothing to do
    }
}
