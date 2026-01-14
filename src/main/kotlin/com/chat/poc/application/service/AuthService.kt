package com.chat.poc.application.service

import com.chat.poc.domain.entity.Admin
import com.chat.poc.domain.entity.User
import com.chat.poc.domain.repository.AdminRepository
import com.chat.poc.domain.repository.UserRepository
import com.chat.poc.infrastructure.jwt.JwtTokenProvider
import com.chat.poc.presentation.dto.AdminResponse
import com.chat.poc.presentation.dto.LoginResponse
import com.chat.poc.presentation.dto.UserResponse
import java.time.format.DateTimeFormatter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
        private val userRepository: UserRepository,
        private val adminRepository: AdminRepository,
        private val jwtTokenProvider: JwtTokenProvider
) {
    companion object {
        const val USER_TYPE_USER = "USER"
        const val USER_TYPE_ADMIN = "ADMIN"

        private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }

    /** User 로그인 (없으면 자동 생성) */
    @Transactional
    fun loginUser(email: String): LoginResponse {
        val user =
                userRepository.findByEmail(email).orElseGet {
                    userRepository.save(User(email = email))
                }

        val token = jwtTokenProvider.createToken(user.id, USER_TYPE_USER)

        return LoginResponse(
                id = user.id,
                email = user.email,
                userType = USER_TYPE_USER,
                accessToken = token
        )
    }

    /** Admin 로그인 (없으면 자동 생성) */
    @Transactional
    fun loginAdmin(email: String): LoginResponse {
        val admin =
                adminRepository.findByEmail(email).orElseGet {
                    adminRepository.save(Admin(email = email, name = extractNameFromEmail(email)))
                }

        val token = jwtTokenProvider.createToken(admin.id, USER_TYPE_ADMIN)

        return LoginResponse(
                id = admin.id,
                email = admin.email,
                userType = USER_TYPE_ADMIN,
                accessToken = token
        )
    }

    /** 현재 로그인한 User 정보 조회 */
    @Transactional(readOnly = true)
    fun getCurrentUser(userId: Long): UserResponse? {
        return userRepository
                .findById(userId)
                .map { user ->
                    UserResponse(
                            id = user.id,
                            email = user.email,
                            createdAt = user.createdAt.format(dateFormatter)
                    )
                }
                .orElse(null)
    }

    /** 현재 로그인한 Admin 정보 조회 */
    @Transactional(readOnly = true)
    fun getCurrentAdmin(adminId: Long): AdminResponse? {
        return adminRepository
                .findById(adminId)
                .map { admin ->
                    AdminResponse(
                            id = admin.id,
                            email = admin.email,
                            name = admin.name,
                            createdAt = admin.createdAt.format(dateFormatter)
                    )
                }
                .orElse(null)
    }

    /** 이메일에서 이름 추출 (POC용 간단 로직) */
    private fun extractNameFromEmail(email: String): String {
        return email.substringBefore("@").replaceFirstChar { it.uppercase() }
    }
}
