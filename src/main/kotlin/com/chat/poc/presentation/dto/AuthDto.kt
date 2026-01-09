package com.chat.poc.presentation.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

/** 로그인 요청 DTO */
data class LoginRequest(
        @field:NotBlank(message = "이메일은 필수입니다")
        @field:Email(message = "올바른 이메일 형식이 아닙니다")
        val email: String
)

/** 로그인 응답 DTO */
data class LoginResponse(
        val id: Long,
        val email: String,
        val userType: String // "USER" or "ADMIN"
)

/** User 정보 응답 DTO */
data class UserResponse(val id: Long, val email: String, val createdAt: String)

/** Admin 정보 응답 DTO */
data class AdminResponse(val id: Long, val email: String, val name: String, val createdAt: String)

/** 공통 API 응답 DTO */
data class ApiResponse<T>(val success: Boolean, val data: T? = null, val message: String? = null) {
    companion object {
        fun <T> success(data: T): ApiResponse<T> = ApiResponse(success = true, data = data)
        fun <T> error(message: String): ApiResponse<T> =
                ApiResponse(success = false, message = message)
    }
}
