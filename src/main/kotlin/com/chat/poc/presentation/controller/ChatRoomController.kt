package com.chat.poc.presentation.controller

import com.chat.poc.application.service.AuthService
import com.chat.poc.application.service.ChatRoomService
import com.chat.poc.presentation.dto.*
import jakarta.servlet.http.HttpSession
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/chatrooms")
class ChatRoomController(
        private val chatRoomService: ChatRoomService,
        private val authService: AuthService
) {

    /** 채팅방 입장 (정보 + 메시지 목록 + 읽음 처리) GET /api/chatrooms/{id} */
    @GetMapping("/{id}")
    fun enterChatRoom(
            @PathVariable id: Long,
            session: HttpSession
    ): ResponseEntity<ApiResponse<ChatRoomDetailResponse>> {
        val userId = authService.getUserIdFromSession(session)
        val adminId = authService.getAdminIdFromSession(session)

        if (userId == null && adminId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("로그인이 필요합니다"))
        }

        // User인 경우 본인 채팅방만 접근 가능
        if (userId != null && !chatRoomService.canUserAccessChatRoom(userId, id)) {
            return ResponseEntity.status(403).body(ApiResponse.error("접근 권한이 없습니다"))
        }

        if (!chatRoomService.existsChatRoom(id)) {
            return ResponseEntity.status(404).body(ApiResponse.error("채팅방을 찾을 수 없습니다"))
        }

        val isAdmin = adminId != null
        val response = chatRoomService.enterChatRoom(id, isAdmin)
        return ResponseEntity.ok(ApiResponse.success(response))
    }

    /** 채팅방 메시지 목록 조회 (페이지네이션) GET /api/chatrooms/{id}/messages */
    @GetMapping("/{id}/messages")
    fun getMessages(
            @PathVariable id: Long,
            @RequestParam(defaultValue = "0") page: Int,
            @RequestParam(defaultValue = "20") size: Int,
            session: HttpSession
    ): ResponseEntity<ApiResponse<MessageListResponse>> {
        val userId = authService.getUserIdFromSession(session)
        val adminId = authService.getAdminIdFromSession(session)

        if (userId == null && adminId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("로그인이 필요합니다"))
        }

        // User인 경우 본인 채팅방만 접근 가능
        if (userId != null && !chatRoomService.canUserAccessChatRoom(userId, id)) {
            return ResponseEntity.status(403).body(ApiResponse.error("접근 권한이 없습니다"))
        }

        if (!chatRoomService.existsChatRoom(id)) {
            return ResponseEntity.status(404).body(ApiResponse.error("채팅방을 찾을 수 없습니다"))
        }

        val response = chatRoomService.getMessages(id, page, size)
        return ResponseEntity.ok(ApiResponse.success(response))
    }
}
