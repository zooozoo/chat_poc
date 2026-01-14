package com.chat.poc.application.service

import com.chat.poc.domain.entity.Message
import com.chat.poc.domain.entity.SenderType
import com.chat.poc.domain.repository.ChatRoomRepository
import com.chat.poc.domain.repository.MessageRepository
import com.chat.poc.domain.repository.UserRepository
import com.chat.poc.infrastructure.redis.RedisPublisher
import com.chat.poc.presentation.dto.ChatMessageResponse
import com.chat.poc.presentation.dto.ChatRoomNotification
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MessageService(
        private val messageRepository: MessageRepository,
        private val chatRoomRepository: ChatRoomRepository,
        private val userRepository: UserRepository,
        private val redisPublisher: RedisPublisher
) {
        private val log = LoggerFactory.getLogger(javaClass)

        companion object {
                private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        }

        /** 메시지 전송 (저장 + Redis 발행) */
        @Transactional
        fun sendMessage(
                chatRoomId: Long,
                senderId: Long,
                senderType: SenderType,
                content: String
        ): ChatMessageResponse {
                val chatRoom =
                        chatRoomRepository.findById(chatRoomId).orElseThrow {
                                IllegalArgumentException("ChatRoom not found: $chatRoomId")
                        }

                val now = LocalDateTime.now()

                // 메시지 저장
                val message =
                        messageRepository.save(
                                Message(
                                        chatRoom = chatRoom,
                                        senderId = senderId,
                                        senderType = senderType,
                                        content = content,
                                        createdAt = now
                                )
                        )

                log.info(
                        "[MSG 💾] Message saved - id: ${message.id}, roomId: $chatRoomId, sender: $senderType($senderId)"
                )

                // 채팅방 마지막 메시지 정보 업데이트
                chatRoom.updateLastMessage(content, now)
                chatRoomRepository.save(chatRoom)

                val response =
                        ChatMessageResponse(
                                id = message.id,
                                chatRoomId = chatRoomId,
                                senderId = senderId,
                                senderType = senderType.name,
                                content = content,
                                createdAt = now.format(dateFormatter)
                        )

                // Redis를 통해 실시간 메시지 전달
                log.info(
                        "[MSG 📤] Publishing to Redis - roomId: $chatRoomId, messageId: ${message.id}"
                )
                redisPublisher.publishMessage(chatRoomId, response)

                // User가 메시지를 보낸 경우 Admin에게 알림
                if (senderType == SenderType.USER) {
                        val unreadCount =
                                messageRepository.countUnreadMessages(chatRoomId, SenderType.USER)
                        val notification =
                                ChatRoomNotification(
                                        chatRoomId = chatRoomId,
                                        userEmail = chatRoom.user.email,
                                        unreadCount = unreadCount,
                                        lastMessageContent = content,
                                        lastMessageAt = now.format(dateFormatter),
                                        assignedAdminId = chatRoom.admin?.id
                                )
                        redisPublisher.publishAdminNotification(notification)
                }

                return response
        }

        /** User 메시지 전송 */
        @Transactional
        fun sendUserMessage(chatRoomId: Long, userId: Long, content: String): ChatMessageResponse {
                return sendMessage(chatRoomId, userId, SenderType.USER, content)
        }

        /** Admin 메시지 전송 */
        @Transactional
        fun sendAdminMessage(
                chatRoomId: Long,
                adminId: Long,
                content: String
        ): ChatMessageResponse {
                return sendMessage(chatRoomId, adminId, SenderType.ADMIN, content)
        }
}
