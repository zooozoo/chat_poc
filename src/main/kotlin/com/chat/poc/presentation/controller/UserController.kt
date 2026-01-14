package com.chat.poc.presentation.controller

import com.chat.poc.application.service.AuthService
import com.chat.poc.application.service.ChatRoomService
import com.chat.poc.infrastructure.jwt.JwtUserPrincipal
import com.chat.poc.presentation.dto.*
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
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
            @Valid @RequestBody request: LoginRequest
    ): ResponseEntity<ApiResponse<LoginResponse>> {
        val response = authService.loginUser(request.email)
        return ResponseEntity.ok(ApiResponse.success(response))
    }

    /** 현재 로그인한 User 정보 조회 GET /api/users/me */
    @GetMapping("/me")
    fun getMe(
            @AuthenticationPrincipal principal: JwtUserPrincipal
    ): ResponseEntity<ApiResponse<UserResponse>> {
        if (!principal.isUser()) {
            return ResponseEntity.status(403).body(ApiResponse.error("User 권한이 필요합니다"))
        }
        val user =
                authService.getCurrentUser(principal.userId)
                        ?: return ResponseEntity.status(404)
                                .body(ApiResponse.error("사용자를 찾을 수 없습니다"))
        return ResponseEntity.ok(ApiResponse.success(user))
    }

    /** User 채팅방 조회 (없으면 자동 생성) GET /api/users/chatroom */
    @GetMapping("/chatroom")
    fun getChatRoom(
            @AuthenticationPrincipal principal: JwtUserPrincipal
    ): ResponseEntity<ApiResponse<ChatRoomResponse>> {
        if (!principal.isUser()) {
            return ResponseEntity.status(403).body(ApiResponse.error("User 권한이 필요합니다"))
        }
        val response = chatRoomService.getOrCreateChatRoomForUser(principal.userId)
        return ResponseEntity.ok(ApiResponse.success(response))
    }
}
