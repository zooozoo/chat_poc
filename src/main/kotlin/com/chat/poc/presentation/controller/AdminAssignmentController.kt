package com.chat.poc.presentation.controller

import com.chat.poc.application.service.ChatRoomService
import com.chat.poc.infrastructure.jwt.JwtUserPrincipal
import com.chat.poc.presentation.dto.ApiResponse
import com.chat.poc.presentation.dto.ChatRoomListResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admins/chatrooms")
class AdminAssignmentController(private val chatRoomService: ChatRoomService) {

    /** 미배정 채팅방 목록 조회 */
    @GetMapping("/unassigned")
    fun getUnassignedChatRooms(
            @AuthenticationPrincipal principal: JwtUserPrincipal
    ): ResponseEntity<ApiResponse<ChatRoomListResponse>> {
        if (!principal.isAdmin()) {
            return ResponseEntity.status(403).body(ApiResponse.error("Admin 권한이 필요합니다"))
        }
        val response = chatRoomService.getUnassignedChatRooms()
        return ResponseEntity.ok(ApiResponse.success(response))
    }

    /** 내 담당 채팅방 목록 조회 */
    @GetMapping("/mine")
    fun getMyChatRooms(
            @AuthenticationPrincipal principal: JwtUserPrincipal
    ): ResponseEntity<ApiResponse<ChatRoomListResponse>> {
        if (!principal.isAdmin()) {
            return ResponseEntity.status(403).body(ApiResponse.error("Admin 권한이 필요합니다"))
        }
        val response = chatRoomService.getMyChatRooms(principal.userId)
        return ResponseEntity.ok(ApiResponse.success(response))
    }

    /** 상담사 배정 (상담 시작) */
    @PostMapping("/{id}/assign")
    fun assignChatRoom(
            @PathVariable id: Long,
            @AuthenticationPrincipal principal: JwtUserPrincipal
    ): ResponseEntity<ApiResponse<Unit?>> {
        if (!principal.isAdmin()) {
            return ResponseEntity.status(403).body(ApiResponse.error("Admin 권한이 필요합니다"))
        }
        chatRoomService.assignChatRoom(id, principal.userId)
        return ResponseEntity.ok(ApiResponse.success(null))
    }
}
