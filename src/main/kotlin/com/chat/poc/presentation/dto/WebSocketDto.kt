package com.chat.poc.presentation.dto

/** WebSocket 메시지 전송 요청 DTO */
data class ChatMessageRequest(val content: String)

/** WebSocket 메시지 응답 DTO (실시간 전달용) */
data class ChatMessageResponse(
        val id: Long,
        val chatRoomId: Long,
        val senderId: Long,
        val senderType: String,
        val content: String,
        val createdAt: String
)

/** 채팅방 알림 DTO (Admin 채팅방 목록 업데이트용) */
data class ChatRoomNotification(
        val chatRoomId: Long,
        val userEmail: String,
        val unreadCount: Long,
        val lastMessageContent: String,
        val lastMessageAt: String
)

/** 읽음 알림 DTO */
data class ReadNotification(
        val chatRoomId: Long,
        val readByType: String, // "USER" or "ADMIN"
        val readAt: String
)
