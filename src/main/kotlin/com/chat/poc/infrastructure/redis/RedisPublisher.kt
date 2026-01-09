package com.chat.poc.infrastructure.redis

import com.chat.poc.presentation.dto.ChatMessageResponse
import com.chat.poc.presentation.dto.ChatRoomAssignmentNotification
import com.chat.poc.presentation.dto.ChatRoomNotification
import com.chat.poc.presentation.dto.ReadNotification
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component

@Component
class RedisPublisher(
        private val redisTemplate: RedisTemplate<String, Any>,
        private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val CHAT_CHANNEL_PREFIX = "chat:room:"
        const val ADMIN_NOTIFICATION_CHANNEL = "chat:admin:notification"

        const val READ_NOTIFICATION_PREFIX = "chat:read:"
        const val ASSIGNMENT_CHANNEL = "chat:admin:assignment"
    }

    /** 채팅방에 메시지 발행 */
    fun publishMessage(chatRoomId: Long, message: ChatMessageResponse) {
        val channel = "$CHAT_CHANNEL_PREFIX$chatRoomId"
        val payload = objectMapper.writeValueAsString(message)
        redisTemplate.convertAndSend(channel, payload)
        log.debug("Published message to channel: $channel")
    }

    /** Admin에게 채팅방 알림 발행 */
    fun publishAdminNotification(notification: ChatRoomNotification) {
        val payload = objectMapper.writeValueAsString(notification)
        redisTemplate.convertAndSend(ADMIN_NOTIFICATION_CHANNEL, payload)
        log.debug("Published admin notification for chatRoom: ${notification.chatRoomId}")
    }

    /** 읽음 상태 알림 발행 */
    fun publishReadNotification(chatRoomId: Long, notification: ReadNotification) {
        val channel = "$READ_NOTIFICATION_PREFIX$chatRoomId"
        val payload = objectMapper.writeValueAsString(notification)
        redisTemplate.convertAndSend(channel, payload)
        log.debug("Published read notification to channel: $channel")
    }

    /** 배정 알림 발행 */
    fun publishAssignmentNotification(notification: ChatRoomAssignmentNotification) {
        val payload = objectMapper.writeValueAsString(notification)
        redisTemplate.convertAndSend(ASSIGNMENT_CHANNEL, payload)
        log.debug("Published assignment notification for chatRoom: ${notification.chatRoomId}")
    }
}
