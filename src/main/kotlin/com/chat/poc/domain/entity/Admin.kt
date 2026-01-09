package com.chat.poc.domain.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "admins")
class Admin(
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
        @Column(nullable = false, unique = true) val email: String,
        @Column(nullable = false) val name: String = "",
        @Column(name = "created_at", nullable = false, updatable = false)
        val createdAt: LocalDateTime = LocalDateTime.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Admin) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
