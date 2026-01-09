package com.chat.poc.domain.repository

import com.chat.poc.domain.entity.Message
import com.chat.poc.domain.entity.SenderType
import java.time.LocalDateTime
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface MessageRepository : JpaRepository<Message, Long> {

    /** 채팅방의 메시지 목록 조회 (페이지네이션, 최신순) */
    fun findByChatRoomIdOrderByCreatedAtDesc(chatRoomId: Long, pageable: Pageable): Page<Message>

    /** 채팅방의 전체 메시지 목록 조회 (시간순) */
    fun findByChatRoomIdOrderByCreatedAtAsc(chatRoomId: Long): List<Message>

    /** 채팅방의 읽지 않은 메시지 수 조회 (특정 발신자 유형 제외) */
    @Query(
            "SELECT COUNT(m) FROM Message m WHERE m.chatRoom.id = :chatRoomId AND m.senderType = :senderType AND m.readAt IS NULL"
    )
    fun countUnreadMessages(
            @Param("chatRoomId") chatRoomId: Long,
            @Param("senderType") senderType: SenderType
    ): Long

    /** 채팅방의 특정 발신자 유형의 모든 메시지 읽음 처리 */
    @Modifying
    @Query(
            "UPDATE Message m SET m.readAt = :readAt WHERE m.chatRoom.id = :chatRoomId AND m.senderType = :senderType AND m.readAt IS NULL"
    )
    fun markAllAsRead(
            @Param("chatRoomId") chatRoomId: Long,
            @Param("senderType") senderType: SenderType,
            @Param("readAt") readAt: LocalDateTime = LocalDateTime.now()
    ): Int
}
