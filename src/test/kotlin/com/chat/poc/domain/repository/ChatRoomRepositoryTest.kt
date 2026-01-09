package com.chat.poc.domain.repository

import com.chat.poc.domain.entity.Admin
import com.chat.poc.domain.entity.ChatRoom
import com.chat.poc.domain.entity.User
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ChatRoomRepositoryTest {

    @Autowired lateinit var chatRoomRepository: ChatRoomRepository

    @Autowired lateinit var userRepository: UserRepository

    @Autowired lateinit var adminRepository: AdminRepository

    @Test
    fun `should find assigned and unassigned chat rooms`() {
        // Given
        val user1 = userRepository.save(User(email = "user1_test@test.com"))
        val user2 = userRepository.save(User(email = "user2_test@test.com"))
        val admin = adminRepository.save(Admin(email = "admin_test@test.com", name = "Admin"))

        val room1 = chatRoomRepository.save(ChatRoom(user = user1)) // Unassigned
        val room2 = chatRoomRepository.save(ChatRoom(user = user2)) // Assigned later

        // When
        room2.assignAdmin(admin)
        chatRoomRepository.save(room2)

        // Then
        val unassigned = chatRoomRepository.findAllByAdminIsNull()
        val assigned = chatRoomRepository.findAllByAdmin(admin)

        assertTrue(unassigned.any { it.id == room1.id }) { "Unassigned list should contain room1" }
        assertTrue(assigned.any { it.id == room2.id }) { "Assigned list should contain room2" }

        // Cleanup (since using real DB)
        chatRoomRepository.delete(room1)
        chatRoomRepository.delete(room2)
        userRepository.delete(user1)
        userRepository.delete(user2)
        adminRepository.delete(admin)
    }
}
