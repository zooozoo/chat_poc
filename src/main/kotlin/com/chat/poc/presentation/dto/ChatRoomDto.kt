package com.chat.poc.presentation.dto

/** 채팅방 정보 응답 DTO (User용) */
data class ChatRoomResponse(
        val id: Long,
        val userEmail: String,
        val unreadCount: Long,
        val lastMessageContent: String?,
        val lastMessageAt: String?,
        val createdAt: String
)

/** 채팅방 목록 응답 DTO (Admin용) */
data class ChatRoomListResponse(val chatRooms: List<ChatRoomSummary>)

/** 채팅방 요약 정보 (목록용) */
data class ChatRoomSummary(
        val id: Long,
        val userId: Long,
        val userEmail: String,
        val unreadCount: Long,
        val lastMessageContent: String?,
        val lastMessageAt: String?,
        val assignedAdminEmail: String? = null,
        val createdAt: String
)

/** 채팅방 상세 정보 (입장 시) */
data class ChatRoomDetailResponse(
        val id: Long,
        val userEmail: String,
        val assignedAdminEmail: String? = null,
        val messages: List<MessageResponse>,
        val createdAt: String
)

/** 메시지 응답 DTO */
data class MessageResponse(
        val id: Long,
        val senderId: Long,
        val senderType: String,
        val content: String,
        val isRead: Boolean,
        val readAt: String?,
        val createdAt: String
)

/** 메시지 목록 응답 DTO (페이지네이션) */
data class MessageListResponse(
        val messages: List<MessageResponse>,
        val page: Int,
        val size: Int,
        val totalElements: Long,
        val totalPages: Int,
        val hasNext: Boolean
)
