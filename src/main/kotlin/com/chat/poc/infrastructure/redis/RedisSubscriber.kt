package com.chat.poc.infrastructure.redis

import com.chat.poc.presentation.dto.ChatMessageResponse
import com.chat.poc.presentation.dto.ChatRoomAssignmentNotification
import com.chat.poc.presentation.dto.ChatRoomNotification
import com.chat.poc.presentation.dto.ReadNotification
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

@Component
class RedisSubscriber(
        private val messagingTemplate: SimpMessagingTemplate,
        private val objectMapper: ObjectMapper
) : MessageListener {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun onMessage(message: Message, pattern: ByteArray?) {
        try {
            val channel = String(message.channel)
            var payload = String(message.body)

            log.info("[Redis ↓] Received from channel: $channel")

            // Redis에서 이중 인코딩된 경우 처리
            if (payload.startsWith("\"") && payload.endsWith("\"")) {
                payload = objectMapper.readValue(payload, String::class.java)
            }

            when {
                channel.startsWith(RedisPublisher.CHAT_CHANNEL_PREFIX) -> {
                    handleChatMessage(channel, payload)
                }
                channel == RedisPublisher.ADMIN_NOTIFICATION_CHANNEL -> {
                    handleAdminNotification(payload)
                }
                channel.startsWith(RedisPublisher.READ_NOTIFICATION_PREFIX) -> {
                    handleReadNotification(channel, payload)
                }
                channel == RedisPublisher.ASSIGNMENT_CHANNEL -> {
                    handleAssignmentNotification(payload)
                }
            }
        } catch (e: Exception) {
            log.error("Error processing Redis message", e)
        }
    }

    private fun handleChatMessage(channel: String, payload: String) {
        val chatRoomId = channel.removePrefix(RedisPublisher.CHAT_CHANNEL_PREFIX)
        val message = objectMapper.readValue(payload, ChatMessageResponse::class.java)
        messagingTemplate.convertAndSend("/topic/chat/$chatRoomId", message)
        log.info("[MSG 📨] Broadcasting to /topic/chat/$chatRoomId - messageId: ${message.id}")
    }

    private fun handleAdminNotification(payload: String) {
        val notification = objectMapper.readValue(payload, ChatRoomNotification::class.java)
        messagingTemplate.convertAndSend("/topic/admin/chatrooms", notification)
        log.info(
                "[NOTIFY 📢] Admin notification - roomId: ${notification.chatRoomId}, unread: ${notification.unreadCount}"
        )
    }

    private fun handleReadNotification(channel: String, payload: String) {
        val chatRoomId = channel.removePrefix(RedisPublisher.READ_NOTIFICATION_PREFIX)
        val notification = objectMapper.readValue(payload, ReadNotification::class.java)

        // 1. 채팅방 내부 사용자에게 알림
        messagingTemplate.convertAndSend("/topic/chat/$chatRoomId/read", notification)

        // 2. 관리자 목록(Sidebar) 업데이트용 알림 (전체 관리자에게 브로드캐스트)
        messagingTemplate.convertAndSend("/topic/admin/reads", notification)

        log.info("[READ 📤] Notification sent to /topic/chat/$chatRoomId/read")
    }

    private fun handleAssignmentNotification(payload: String) {
        val notification =
                objectMapper.readValue(payload, ChatRoomAssignmentNotification::class.java)
        messagingTemplate.convertAndSend("/topic/admin/assignments", notification)
        log.info("[ASSIGN 📤] Assignment notification - roomId: ${notification.chatRoomId}")
    }
}
