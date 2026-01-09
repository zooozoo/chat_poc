package com.chat.poc.domain.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "chat_rooms")
class ChatRoom(
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "user_id", nullable = false)
        val user: User,
        @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "admin_id") var admin: Admin? = null,
        @Column(name = "last_message_content", columnDefinition = "TEXT")
        var lastMessageContent: String? = null,
        @Column(name = "last_message_at") var lastMessageAt: LocalDateTime? = null,
        @Column(name = "created_at", nullable = false, updatable = false)
        val createdAt: LocalDateTime = LocalDateTime.now(),
        @OneToMany(mappedBy = "chatRoom", cascade = [CascadeType.ALL], orphanRemoval = true)
        val messages: MutableList<Message> = mutableListOf()
) {
    /** 마지막 메시지 정보 업데이트 */
    fun updateLastMessage(content: String, messageAt: LocalDateTime = LocalDateTime.now()) {
        this.lastMessageContent = content
        this.lastMessageAt = messageAt
    }

    /** 상담사 배정 */
    fun assignAdmin(admin: Admin) {
        this.admin = admin
    }

    /** 배정 해제 (필요시) */
    fun unassignAdmin() {
        this.admin = null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChatRoom) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
