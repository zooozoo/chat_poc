package com.chat.poc.infrastructure.jwt

import java.security.Principal

/** JWT 인증 후 SecurityContext에 저장되는 사용자 정보 */
data class JwtUserPrincipal(val userId: Long, val userType: String) : Principal {
    override fun getName(): String = userId.toString()

    fun isUser(): Boolean = userType == "USER"
    fun isAdmin(): Boolean = userType == "ADMIN"
}
