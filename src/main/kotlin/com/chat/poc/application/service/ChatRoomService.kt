package com.chat.poc.application.service

import com.chat.poc.domain.entity.ChatRoom
import com.chat.poc.domain.entity.Message
import com.chat.poc.domain.entity.SenderType
import com.chat.poc.domain.repository.AdminRepository
import com.chat.poc.domain.repository.ChatRoomRepository
import com.chat.poc.domain.repository.MessageRepository
import com.chat.poc.domain.repository.UserRepository
import com.chat.poc.infrastructure.redis.RedisPublisher
import com.chat.poc.presentation.dto.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ChatRoomService(
        private val chatRoomRepository: ChatRoomRepository,
        private val messageRepository: MessageRepository,
        private val userRepository: UserRepository,
        private val adminRepository: AdminRepository,
        private val redisPublisher: RedisPublisher
) {
    companion object {
        private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }

    /** User의 채팅방 조회 (없으면 자동 생성) */
    @Transactional
    fun getOrCreateChatRoomForUser(userId: Long): ChatRoomResponse {
        val user =
                userRepository.findById(userId).orElseThrow {
                    IllegalArgumentException("User not found: $userId")
                }

        val chatRoom =
                chatRoomRepository.findByUserId(userId).orElseGet {
                    chatRoomRepository.save(ChatRoom(user = user))
                }

        // Admin이 보낸 메시지 중 읽지 않은 수
        val unreadCount = messageRepository.countUnreadMessages(chatRoom.id, SenderType.ADMIN)

        return ChatRoomResponse(
                id = chatRoom.id,
                userEmail = user.email,
                unreadCount = unreadCount,
                lastMessageContent = chatRoom.lastMessageContent,
                lastMessageAt = chatRoom.lastMessageAt?.format(dateFormatter),
                createdAt = chatRoom.createdAt.format(dateFormatter)
        )
    }

    /** Admin용 모든 채팅방 목록 조회 */
    @Transactional(readOnly = true)
    fun getAllChatRoomsForAdmin(): ChatRoomListResponse {
        val chatRooms = chatRoomRepository.findAllWithUser()

        val summaries =
                chatRooms.map { chatRoom ->
                    // User가 보낸 메시지 중 읽지 않은 수
                    val unreadCount =
                            messageRepository.countUnreadMessages(chatRoom.id, SenderType.USER)

                    ChatRoomSummary(
                            id = chatRoom.id,
                            userId = chatRoom.user.id,
                            userEmail = chatRoom.user.email,
                            unreadCount = unreadCount,
                            lastMessageContent = chatRoom.lastMessageContent,
                            lastMessageAt = chatRoom.lastMessageAt?.format(dateFormatter),
                            assignedAdminEmail = chatRoom.admin?.email,
                            createdAt = chatRoom.createdAt.format(dateFormatter)
                    )
                }

        return ChatRoomListResponse(chatRooms = summaries)
    }

    /** 채팅방 입장 (상세 정보 + 메시지 목록 + 읽음 처리) */
    @Transactional
    fun enterChatRoom(chatRoomId: Long, isAdmin: Boolean): ChatRoomDetailResponse {
        val chatRoom =
                chatRoomRepository.findById(chatRoomId).orElseThrow {
                    IllegalArgumentException("ChatRoom not found: $chatRoomId")
                }

        // 상대방 메시지 읽음 처리 로직은 WebSocket (markAsRead)으로 이관됨.
        // 여기서는 읽음 처리를 수행하지 않고 데이터만 반환함.

        // 메시지 목록 조회 (시간순)
        val messages = messageRepository.findByChatRoomIdOrderByCreatedAtAsc(chatRoomId)

        return ChatRoomDetailResponse(
                id = chatRoom.id,
                userEmail = chatRoom.user.email,
                assignedAdminEmail = chatRoom.admin?.email,
                messages = messages.map { it.toResponse() },
                createdAt = chatRoom.createdAt.format(dateFormatter)
        )
    }

    /** 메시지 읽음 처리 (WebSocket용) */
    @Transactional
    fun markAsRead(chatRoomId: Long, userId: Long, userType: String) {
        val chatRoom =
                chatRoomRepository.findById(chatRoomId).orElseThrow {
                    IllegalArgumentException("ChatRoom not found: $chatRoomId")
                }

        // 요청한 유저가 권한이 있는지 확인
        if (userType == AuthService.USER_TYPE_USER) {
            if (chatRoom.user.id != userId) {
                // 내 방이 아님
                return
            }
        } else if (userType == AuthService.USER_TYPE_ADMIN) {
            // 미배정 상태거나, 내가 배정된 방이 아니면 접근 불가?
            // 일반적으로 Admin은 모든 방을 볼 수 있지만, 여기서는 배정 논리를 따를지 결정해야 함.
            // 일단 기존 enterChatRoom 로직 상 Admin은 모든 방 접근 가능했음.
            // 다만, 배정된 방이 있는데 다른 Admin이 읽는 것은 이상할 수 있음.
            // 여기서는 단순하게 "Admin이면 읽을 수 있다"로 처리하거나, 배정된 경우만 허용할 수 있음.
            // 기존 로직 유지: Admin은 권한 체크가 느슨했음.
        } else {
            return
        }

        // 내가 읽었으니, '상대방이 보낸 메시지'를 읽음 처리해야 함.
        // 내가 User면 -> Admin이 보낸 메시지(SenderType.ADMIN)를 읽음 처리
        // 내가 Admin이면 -> User가 보낸 메시지(SenderType.USER)를 읽음 처리
        // 여기서 userType 파라미터를 신뢰함/
        val senderTypeToMarkRead =
                if (userType == AuthService.USER_TYPE_ADMIN) SenderType.USER else SenderType.ADMIN

        val markedCount = messageRepository.markAllAsRead(chatRoomId, senderTypeToMarkRead)

        if (markedCount > 0) {
            val readByType = if (userType == AuthService.USER_TYPE_ADMIN) "ADMIN" else "USER"
            val notification =
                    ReadNotification(
                            chatRoomId = chatRoomId,
                            readByType = readByType,
                            readAt = LocalDateTime.now().format(dateFormatter)
                    )
            redisPublisher.publishReadNotification(chatRoomId, notification)
        }
    }

    /** 채팅방 메시지 목록 조회 (페이지네이션) */
    @Transactional(readOnly = true)
    fun getMessages(chatRoomId: Long, page: Int, size: Int): MessageListResponse {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val messagePage =
                messageRepository.findByChatRoomIdOrderByCreatedAtDesc(chatRoomId, pageable)

        return MessageListResponse(
                messages = messagePage.content.map { it.toResponse() },
                page = messagePage.number,
                size = messagePage.size,
                totalElements = messagePage.totalElements,
                totalPages = messagePage.totalPages,
                hasNext = messagePage.hasNext()
        )
    }

    /** 채팅방 존재 여부 확인 */
    @Transactional(readOnly = true)
    fun existsChatRoom(chatRoomId: Long): Boolean {
        return chatRoomRepository.existsById(chatRoomId)
    }

    /** 채팅방 접근 권한 확인 (User용) */
    @Transactional(readOnly = true)
    fun canUserAccessChatRoom(userId: Long, chatRoomId: Long): Boolean {
        return chatRoomRepository.findById(chatRoomId).map { it.user.id == userId }.orElse(false)
    }

    private fun Message.toResponse(): MessageResponse {
        return MessageResponse(
                id = this.id,
                senderId = this.senderId,
                senderType = this.senderType.name,
                content = this.content,
                isRead = this.isRead(),
                readAt = this.readAt?.format(dateFormatter),
                createdAt = this.createdAt.format(dateFormatter)
        )
    }
    /** 상담사에게 채팅방 배정 */
    @Transactional
    fun assignChatRoom(chatRoomId: Long, adminId: Long) {
        val chatRoom =
                chatRoomRepository.findById(chatRoomId).orElseThrow {
                    IllegalArgumentException("ChatRoom not found: $chatRoomId")
                }

        if (chatRoom.admin != null) {
            throw IllegalStateException("ChatRoom is already assigned to ${chatRoom.admin?.email}")
        }

        val admin =
                adminRepository.findById(adminId).orElseThrow {
                    IllegalArgumentException("Admin not found: $adminId")
                }

        chatRoom.assignAdmin(admin)
        chatRoomRepository.save(chatRoom)

        chatRoomRepository.save(chatRoom)

        // 알림 발행
        val notification =
                ChatRoomAssignmentNotification(
                        chatRoomId = chatRoom.id,
                        assignedAdminId = admin.id,
                        assignedAdminEmail = admin.email,
                        assignedAt = LocalDateTime.now().format(dateFormatter)
                )
        redisPublisher.publishAssignmentNotification(notification)
    }

    /** 미배정 채팅방 목록 조회 */
    @Transactional(readOnly = true)
    fun getUnassignedChatRooms(): ChatRoomListResponse {
        val chatRooms = chatRoomRepository.findAllByAdminIsNull()
        return toListResponse(chatRooms)
    }

    /** 내 담당 채팅방 목록 조회 */
    @Transactional(readOnly = true)
    fun getMyChatRooms(adminId: Long): ChatRoomListResponse {
        val admin =
                adminRepository.findById(adminId).orElseThrow {
                    IllegalArgumentException("Admin not found: $adminId")
                }
        val chatRooms = chatRoomRepository.findAllByAdmin(admin)
        return toListResponse(chatRooms)
    }

    private fun toListResponse(chatRooms: List<ChatRoom>): ChatRoomListResponse {
        val summaries =
                chatRooms.map { chatRoom ->
                    val unreadCount =
                            messageRepository.countUnreadMessages(chatRoom.id, SenderType.USER)

                    ChatRoomSummary(
                            id = chatRoom.id,
                            userId = chatRoom.user.id,
                            userEmail = chatRoom.user.email,
                            unreadCount = unreadCount,
                            lastMessageContent = chatRoom.lastMessageContent,
                            lastMessageAt = chatRoom.lastMessageAt?.format(dateFormatter),
                            assignedAdminEmail = chatRoom.admin?.email,
                            createdAt = chatRoom.createdAt.format(dateFormatter)
                    )
                }
        return ChatRoomListResponse(chatRooms = summaries)
    }
}
