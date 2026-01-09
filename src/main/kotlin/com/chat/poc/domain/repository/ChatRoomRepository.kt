package com.chat.poc.domain.repository

import com.chat.poc.domain.entity.ChatRoom
import com.chat.poc.domain.entity.User
import java.util.Optional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ChatRoomRepository : JpaRepository<ChatRoom, Long> {
    fun findByUser(user: User): Optional<ChatRoom>
    fun findByUserId(userId: Long): Optional<ChatRoom>

    @Query("SELECT cr FROM ChatRoom cr JOIN FETCH cr.user") fun findAllWithUser(): List<ChatRoom>
}
