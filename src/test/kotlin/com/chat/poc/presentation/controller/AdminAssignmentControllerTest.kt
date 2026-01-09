package com.chat.poc.presentation.controller

import com.chat.poc.application.service.AuthService
import com.chat.poc.application.service.ChatRoomService
import com.chat.poc.presentation.dto.ChatRoomListResponse
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(AdminAssignmentController::class)
class AdminAssignmentControllerTest {

    @Autowired lateinit var mockMvc: MockMvc

    @MockBean lateinit var chatRoomService: ChatRoomService

    // Auth Check relies on session, no need to mock AuthService bean if only session attribute is
    // checked in controller.
    // However, if Controller uses AuthService constant, that's static/companion.

    @Test
    fun `getUnassignedChatRooms should return 200`() {
        // Given
        val session = MockHttpSession()
        session.setAttribute(AuthService.SESSION_USER_ID, 1L)
        session.setAttribute(AuthService.SESSION_USER_TYPE, AuthService.USER_TYPE_ADMIN)

        given(chatRoomService.getUnassignedChatRooms())
                .willReturn(ChatRoomListResponse(emptyList()))

        // When & Then
        mockMvc.perform(get("/api/admins/chatrooms/unassigned").session(session))
                .andExpect(status().isOk)

        verify(chatRoomService).getUnassignedChatRooms()
    }

    @Test
    fun `assignChatRoom should return 200`() {
        // Given
        val session = MockHttpSession()
        session.setAttribute(AuthService.SESSION_USER_ID, 1L)
        session.setAttribute(AuthService.SESSION_USER_TYPE, AuthService.USER_TYPE_ADMIN)

        // When & Then
        mockMvc.perform(post("/api/admins/chatrooms/1/assign").session(session))
                .andExpect(status().isOk)

        verify(chatRoomService).assignChatRoom(1L, 1L)
    }
}
