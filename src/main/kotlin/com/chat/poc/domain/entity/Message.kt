package com.chat.poc.domain.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/** 메시지 발신자 유형 */
enum class SenderType {
    USER, // 앱 사용자
    ADMIN // 운영자
}

@Entity
@Table(name = "messages")
class Message(
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "chat_room_id", nullable = false)
        val chatRoom: ChatRoom,
        @Column(name = "sender_id", nullable = false) val senderId: Long,
        @Enumerated(EnumType.STRING)
        @Column(name = "sender_type", nullable = false)
        val senderType: SenderType,
        @Column(nullable = false, columnDefinition = "TEXT") val content: String,
        @Column(name = "read_at") var readAt: LocalDateTime? = null,
        @Column(name = "created_at", nullable = false, updatable = false)
        val createdAt: LocalDateTime = LocalDateTime.now()
) {
    /** 메시지 읽음 처리 */
    fun markAsRead() {
        if (readAt == null) {
            readAt = LocalDateTime.now()
        }
    }

    /** 메시지가 읽혔는지 확인 */
    fun isRead(): Boolean = readAt != null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Message) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
