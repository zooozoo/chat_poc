package com.chat.poc.application.service

import com.chat.poc.domain.entity.Admin
import com.chat.poc.domain.entity.User
import com.chat.poc.domain.repository.AdminRepository
import com.chat.poc.domain.repository.UserRepository
import com.chat.poc.presentation.dto.AdminResponse
import com.chat.poc.presentation.dto.LoginResponse
import com.chat.poc.presentation.dto.UserResponse
import jakarta.servlet.http.HttpSession
import java.time.format.DateTimeFormatter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
        private val userRepository: UserRepository,
        private val adminRepository: AdminRepository
) {
    companion object {
        const val SESSION_USER_ID = "userId"
        const val SESSION_USER_TYPE = "userType"
        const val USER_TYPE_USER = "USER"
        const val USER_TYPE_ADMIN = "ADMIN"

        private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }

    /** User 로그인 (없으면 자동 생성) */
    @Transactional
    fun loginUser(email: String, session: HttpSession): LoginResponse {
        val user =
                userRepository.findByEmail(email).orElseGet {
                    userRepository.save(User(email = email))
                }

        session.setAttribute(SESSION_USER_ID, user.id)
        session.setAttribute(SESSION_USER_TYPE, USER_TYPE_USER)

        return LoginResponse(id = user.id, email = user.email, userType = USER_TYPE_USER)
    }

    /** Admin 로그인 (없으면 자동 생성) */
    @Transactional
    fun loginAdmin(email: String, session: HttpSession): LoginResponse {
        val admin =
                adminRepository.findByEmail(email).orElseGet {
                    adminRepository.save(Admin(email = email, name = extractNameFromEmail(email)))
                }

        session.setAttribute(SESSION_USER_ID, admin.id)
        session.setAttribute(SESSION_USER_TYPE, USER_TYPE_ADMIN)

        return LoginResponse(id = admin.id, email = admin.email, userType = USER_TYPE_ADMIN)
    }

    /** 로그아웃 */
    fun logout(session: HttpSession) {
        session.invalidate()
    }

    /** 현재 로그인한 User 정보 조회 */
    @Transactional(readOnly = true)
    fun getCurrentUser(session: HttpSession): UserResponse? {
        val userId = session.getAttribute(SESSION_USER_ID) as? Long ?: return null
        val userType = session.getAttribute(SESSION_USER_TYPE) as? String

        if (userType != USER_TYPE_USER) return null

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
    fun getCurrentAdmin(session: HttpSession): AdminResponse? {
        val adminId = session.getAttribute(SESSION_USER_ID) as? Long ?: return null
        val userType = session.getAttribute(SESSION_USER_TYPE) as? String

        if (userType != USER_TYPE_ADMIN) return null

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

    /** 세션에서 User ID 추출 */
    fun getUserIdFromSession(session: HttpSession): Long? {
        val userType = session.getAttribute(SESSION_USER_TYPE) as? String
        if (userType != USER_TYPE_USER) return null
        return session.getAttribute(SESSION_USER_ID) as? Long
    }

    /** 세션에서 Admin ID 추출 */
    fun getAdminIdFromSession(session: HttpSession): Long? {
        val userType = session.getAttribute(SESSION_USER_TYPE) as? String
        if (userType != USER_TYPE_ADMIN) return null
        return session.getAttribute(SESSION_USER_ID) as? Long
    }

    /** 이메일에서 이름 추출 (POC용 간단 로직) */
    private fun extractNameFromEmail(email: String): String {
        return email.substringBefore("@").replaceFirstChar { it.uppercase() }
    }
}
