package com.chat.poc.domain.repository

import com.chat.poc.domain.entity.Admin
import java.util.Optional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AdminRepository : JpaRepository<Admin, Long> {
    fun findByEmail(email: String): Optional<Admin>
    fun existsByEmail(email: String): Boolean
}
