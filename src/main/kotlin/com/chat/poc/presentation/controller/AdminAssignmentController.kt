package com.chat.poc.presentation.controller

import com.chat.poc.application.service.AuthService
import com.chat.poc.application.service.ChatRoomService
import com.chat.poc.presentation.dto.ApiResponse
import com.chat.poc.presentation.dto.ChatRoomListResponse
import jakarta.servlet.http.HttpSession
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admins/chatrooms")
class AdminAssignmentController(private val chatRoomService: ChatRoomService) {

    /** 미배정 채팅방 목록 조회 */
    @GetMapping("/unassigned")
    fun getUnassignedChatRooms(session: HttpSession): ApiResponse<ChatRoomListResponse> {
        checkAdminLogin(session)
        val response = chatRoomService.getUnassignedChatRooms()
        return ApiResponse.success(response)
    }

    /** 내 담당 채팅방 목록 조회 */
    @GetMapping("/mine")
    fun getMyChatRooms(session: HttpSession): ApiResponse<ChatRoomListResponse> {
        val adminId = checkAdminLogin(session)
        val response = chatRoomService.getMyChatRooms(adminId)
        return ApiResponse.success(response)
    }

    /** 상담사 배정 (상담 시작) */
    @PostMapping("/{id}/assign")
    fun assignChatRoom(@PathVariable id: Long, session: HttpSession): ApiResponse<Unit?> {
        val adminId = checkAdminLogin(session)
        chatRoomService.assignChatRoom(id, adminId)
        return ApiResponse.success(null)
    }

    private fun checkAdminLogin(session: HttpSession): Long {
        val userId = session.getAttribute(AuthService.SESSION_USER_ID) as? Long
        val userType = session.getAttribute(AuthService.SESSION_USER_TYPE) as? String

        if (userId == null || userType != AuthService.USER_TYPE_ADMIN) {
            throw IllegalStateException("Unauthorized: Admin login required")
        }
        return userId
    }
}
