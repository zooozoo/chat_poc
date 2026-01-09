package com.chat.poc.presentation.controller

import com.chat.poc.application.service.AuthService
import com.chat.poc.application.service.ChatRoomService
import com.chat.poc.application.service.MessageService
import com.chat.poc.presentation.dto.ChatMessageRequest
import com.chat.poc.presentation.dto.ChatMessageResponse
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.stereotype.Controller

@Controller
class ChatWebSocketController(
        private val messageService: MessageService,
        private val chatRoomService: ChatRoomService,
        private val authService: AuthService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 메시지 전송 Client -> /app/chat/{roomId}/send Server -> /topic/chat/{roomId} (Redis Pub/Sub 통해)
     */
    @MessageMapping("/chat/{roomId}/send")
    fun sendMessage(
            @DestinationVariable roomId: Long,
            @Payload request: ChatMessageRequest,
            headerAccessor: SimpMessageHeaderAccessor
    ): ChatMessageResponse? {
        try {
            val sessionAttributes =
                    headerAccessor.sessionAttributes
                            ?: run {
                                log.warn("No session attributes found")
                                return null
                            }

            val userId = sessionAttributes[AuthService.SESSION_USER_ID] as? Long
            val userType = sessionAttributes[AuthService.SESSION_USER_TYPE] as? String

            if (userId == null || userType == null) {
                log.warn("User not authenticated")
                return null
            }

            // 채팅방 접근 권한 확인
            if (userType == AuthService.USER_TYPE_USER) {
                if (!chatRoomService.canUserAccessChatRoom(userId, roomId)) {
                    log.warn("User $userId cannot access chatRoom $roomId")
                    return null
                }
                return messageService.sendUserMessage(roomId, userId, request.content)
            } else {
                return messageService.sendAdminMessage(roomId, userId, request.content)
            }
        } catch (e: Exception) {
            log.error("Error sending message", e)
            return null
        }
    }
}
