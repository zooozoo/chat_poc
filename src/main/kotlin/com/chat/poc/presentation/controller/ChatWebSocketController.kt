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

            log.info("[MSG →] Received message from $userType($userId) to room $roomId")

            // 채팅방 접근 권한 확인
            if (userType == AuthService.USER_TYPE_USER) {
                if (!chatRoomService.canUserAccessChatRoom(userId, roomId)) {
                    log.warn("User $userId cannot access chatRoom $roomId")
                    return null
                }
                val response = messageService.sendUserMessage(roomId, userId, request.content)
                log.info("[MSG ✓] Message sent - roomId: $roomId, senderId: $userId")
                return response
            } else {
                val response = messageService.sendAdminMessage(roomId, userId, request.content)
                log.info("[MSG ✓] Message sent - roomId: $roomId, senderId: $userId")
                return response
            }
        } catch (e: Exception) {
            log.error("Error sending message", e)
            return null
        }
    }

    /** 메시지 읽음 처리 Client -> /app/chat/{roomId}/read */
    @MessageMapping("/chat/{roomId}/read")
    fun readMessage(@DestinationVariable roomId: Long, headerAccessor: SimpMessageHeaderAccessor) {
        try {
            val sessionAttributes =
                    headerAccessor.sessionAttributes
                            ?: run {
                                log.warn("No session attributes found")
                                return
                            }

            val userId = sessionAttributes[AuthService.SESSION_USER_ID] as? Long
            val userType = sessionAttributes[AuthService.SESSION_USER_TYPE] as? String

            if (userId != null && userType != null) {
                chatRoomService.markAsRead(roomId, userId, userType)
                log.info("[READ] Room $roomId marked as read by $userType($userId)")
            }
        } catch (e: Exception) {
            log.error("Error processing read receipt", e)
        }
    }
}
