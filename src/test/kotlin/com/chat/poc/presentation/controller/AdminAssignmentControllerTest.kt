package com.chat.poc.presentation.controller

import com.chat.poc.application.service.ChatRoomService
import com.chat.poc.infrastructure.jwt.JwtTokenProvider
import com.chat.poc.presentation.dto.ChatRoomListResponse
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class AdminAssignmentControllerTest {

    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var jwtTokenProvider: JwtTokenProvider

    @MockBean lateinit var chatRoomService: ChatRoomService

    @Test
    fun `getUnassignedChatRooms should return 200`() {
        // Given
        val token = jwtTokenProvider.createToken(1L, "ADMIN")

        given(chatRoomService.getUnassignedChatRooms())
                .willReturn(ChatRoomListResponse(emptyList()))

        // When & Then
        mockMvc.perform(
                        get("/api/admins/chatrooms/unassigned")
                                .header("Authorization", "Bearer $token")
                )
                .andExpect(status().isOk)

        verify(chatRoomService).getUnassignedChatRooms()
    }

    @Test
    fun `assignChatRoom should return 200`() {
        // Given
        val token = jwtTokenProvider.createToken(1L, "ADMIN")

        // When & Then
        mockMvc.perform(
                        post("/api/admins/chatrooms/1/assign")
                                .header("Authorization", "Bearer $token")
                )
                .andExpect(status().isOk)

        verify(chatRoomService).assignChatRoom(1L, 1L)
    }

    @Test
    fun `request without token should return 403`() {
        // When & Then
        mockMvc.perform(get("/api/admins/chatrooms/unassigned")).andExpect(status().isForbidden)
    }
}
