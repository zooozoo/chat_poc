package com.chat.poc.presentation.controller

import com.chat.poc.application.service.AuthService
import com.chat.poc.application.service.ChatRoomService
import com.chat.poc.presentation.dto.*
import jakarta.servlet.http.HttpSession
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/users")
class UserController(
        private val authService: AuthService,
        private val chatRoomService: ChatRoomService
) {

    /** User 로그인 POST /api/users/login */
    @PostMapping("/login")
    fun login(
            @Valid @RequestBody request: LoginRequest,
            session: HttpSession
    ): ResponseEntity<ApiResponse<LoginResponse>> {
        val response = authService.loginUser(request.email, session)
        return ResponseEntity.ok(ApiResponse.success(response))
    }

    /** User 로그아웃 POST /api/users/logout */
    @PostMapping("/logout")
    fun logout(session: HttpSession): ResponseEntity<ApiResponse<String>> {
        authService.logout(session)
        return ResponseEntity.ok(ApiResponse.success("로그아웃 되었습니다"))
    }

    /** 현재 로그인한 User 정보 조회 GET /api/users/me */
    @GetMapping("/me")
    fun getMe(session: HttpSession): ResponseEntity<ApiResponse<UserResponse>> {
        val user =
                authService.getCurrentUser(session)
                        ?: return ResponseEntity.status(401).body(ApiResponse.error("로그인이 필요합니다"))
        return ResponseEntity.ok(ApiResponse.success(user))
    }

    /** User 채팅방 조회 (없으면 자동 생성) GET /api/users/chatroom */
    @GetMapping("/chatroom")
    fun getChatRoom(session: HttpSession): ResponseEntity<ApiResponse<ChatRoomResponse>> {
        val userId =
                authService.getUserIdFromSession(session)
                        ?: return ResponseEntity.status(401).body(ApiResponse.error("로그인이 필요합니다"))

        val response = chatRoomService.getOrCreateChatRoomForUser(userId)
        return ResponseEntity.ok(ApiResponse.success(response))
    }
}
