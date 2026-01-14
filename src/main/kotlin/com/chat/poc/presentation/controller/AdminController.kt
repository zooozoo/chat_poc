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
@RequestMapping("/api/admins")
class AdminController(
        private val authService: AuthService,
        private val chatRoomService: ChatRoomService
) {

    /** Admin 로그인 POST /api/admins/login */
    @PostMapping("/login")
    fun login(
            @Valid @RequestBody request: LoginRequest
    ): ResponseEntity<ApiResponse<LoginResponse>> {
        val response = authService.loginAdmin(request.email)
        return ResponseEntity.ok(ApiResponse.success(response))
    }

    /** 현재 로그인한 Admin 정보 조회 GET /api/admins/me */
    @GetMapping("/me")
    fun getMe(
            @AuthenticationPrincipal principal: JwtUserPrincipal
    ): ResponseEntity<ApiResponse<AdminResponse>> {
        if (!principal.isAdmin()) {
            return ResponseEntity.status(403).body(ApiResponse.error("Admin 권한이 필요합니다"))
        }
        val admin =
                authService.getCurrentAdmin(principal.userId)
                        ?: return ResponseEntity.status(404)
                                .body(ApiResponse.error("관리자를 찾을 수 없습니다"))
        return ResponseEntity.ok(ApiResponse.success(admin))
    }

    /** 모든 채팅방 목록 조회 GET /api/admins/chatrooms */
    @GetMapping("/chatrooms")
    fun getChatRooms(
            @AuthenticationPrincipal principal: JwtUserPrincipal
    ): ResponseEntity<ApiResponse<ChatRoomListResponse>> {
        if (!principal.isAdmin()) {
            return ResponseEntity.status(403).body(ApiResponse.error("Admin 권한이 필요합니다"))
        }
        val response = chatRoomService.getAllChatRoomsForAdmin()
        return ResponseEntity.ok(ApiResponse.success(response))
    }
}
