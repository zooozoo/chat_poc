package com.chat.poc.infrastructure.jwt

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.util.Date
import javax.crypto.SecretKey
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class JwtTokenProvider(
        @Value("\${jwt.secret}") private val secret: String,
        @Value("\${jwt.expiration}") private val expiration: Long
) {
    private val key: SecretKey by lazy { Keys.hmacShaKeyFor(secret.toByteArray()) }

    companion object {
        const val CLAIM_USER_ID = "userId"
        const val CLAIM_USER_TYPE = "userType"
    }

    /** JWT 토큰 생성 */
    fun createToken(userId: Long, userType: String): String {
        val now = Date()
        val expiryDate = Date(now.time + expiration)

        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_USER_TYPE, userType)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact()
    }

    /** 토큰 유효성 검증 */
    fun validateToken(token: String): Boolean {
        return try {
            val claims = parseClaims(token)
            !claims.expiration.before(Date())
        } catch (e: Exception) {
            false
        }
    }

    /** 토큰에서 userId 추출 */
    fun getUserId(token: String): Long {
        val claims = parseClaims(token)
        return claims[CLAIM_USER_ID].toString().toLong()
    }

    /** 토큰에서 userType 추출 */
    fun getUserType(token: String): String {
        val claims = parseClaims(token)
        return claims[CLAIM_USER_TYPE].toString()
    }

    private fun parseClaims(token: String): Claims {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
    }
}
