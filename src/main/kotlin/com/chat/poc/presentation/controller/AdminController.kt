package com.chat.poc.presentation.controller

import com.chat.poc.application.service.AuthService
import com.chat.poc.application.service.ChatRoomService
import com.chat.poc.presentation.dto.*
import jakarta.servlet.http.HttpSession
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admins")
class AdminController(
        private val authService: AuthService,
        private val chatRoomService: ChatRoomService
) {

    /** Admin 로그인 POST /api/admins/login */
    @PostMapping("/login")
    fun login(
            @Valid @RequestBody request: LoginRequest,
            session: HttpSession
    ): ResponseEntity<ApiResponse<LoginResponse>> {
        val response = authService.loginAdmin(request.email, session)
        return ResponseEntity.ok(ApiResponse.success(response))
    }

    /** Admin 로그아웃 POST /api/admins/logout */
    @PostMapping("/logout")
    fun logout(session: HttpSession): ResponseEntity<ApiResponse<String>> {
        authService.logout(session)
        return ResponseEntity.ok(ApiResponse.success("로그아웃 되었습니다"))
    }

    /** 현재 로그인한 Admin 정보 조회 GET /api/admins/me */
    @GetMapping("/me")
    fun getMe(session: HttpSession): ResponseEntity<ApiResponse<AdminResponse>> {
        val admin =
                authService.getCurrentAdmin(session)
                        ?: return ResponseEntity.status(401).body(ApiResponse.error("로그인이 필요합니다"))
        return ResponseEntity.ok(ApiResponse.success(admin))
    }

    /** 모든 채팅방 목록 조회 GET /api/admins/chatrooms */
    @GetMapping("/chatrooms")
    fun getChatRooms(session: HttpSession): ResponseEntity<ApiResponse<ChatRoomListResponse>> {
        authService.getAdminIdFromSession(session)
                ?: return ResponseEntity.status(401).body(ApiResponse.error("로그인이 필요합니다"))

        val response = chatRoomService.getAllChatRoomsForAdmin()
        return ResponseEntity.ok(ApiResponse.success(response))
    }
}
