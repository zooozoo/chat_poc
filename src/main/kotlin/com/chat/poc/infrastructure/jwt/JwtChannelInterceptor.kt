package com.chat.poc.infrastructure.jwt

import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.stereotype.Component

/** STOMP CONNECT 시 Authorization 헤더에서 JWT 토큰을 추출하고 검증하는 인터셉터 */
@Component
class JwtChannelInterceptor(private val jwtTokenProvider: JwtTokenProvider) : ChannelInterceptor {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
        const val ATTR_USER_ID = "userId"
        const val ATTR_USER_TYPE = "userType"
    }

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*>? {
        val accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
                        ?: return message

        if (accessor.command == StompCommand.CONNECT) {
            val authHeader = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER)

            if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
                val token = authHeader.substring(BEARER_PREFIX.length)

                if (jwtTokenProvider.validateToken(token)) {
                    val userId = jwtTokenProvider.getUserId(token)
                    val userType = jwtTokenProvider.getUserType(token)

                    // sessionAttributes에 사용자 정보 저장 (ChatWebSocketController에서 사용)
                    accessor.sessionAttributes = accessor.sessionAttributes ?: mutableMapOf()
                    accessor.sessionAttributes!![ATTR_USER_ID] = userId
                    accessor.sessionAttributes!![ATTR_USER_TYPE] = userType

                    log.info(
                            "[WS ✓] STOMP CONNECT authenticated - userId: $userId, userType: $userType"
                    )
                } else {
                    log.warn("[WS ✗] Invalid JWT token in STOMP CONNECT")
                    // 연결 거부하려면 null 반환 또는 예외 발생
                    // POC이므로 일단 로그만 남기고 진행
                }
            } else {
                log.warn("[WS ⚠] No Authorization header in STOMP CONNECT")
            }
        }

        return message
    }
}
