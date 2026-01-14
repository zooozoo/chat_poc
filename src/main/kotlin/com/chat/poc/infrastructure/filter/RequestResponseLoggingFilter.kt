package com.chat.poc.infrastructure.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * HTTP API 요청/응답 로깅 필터
 * 모든 API 요청의 시작과 완료를 로깅하여 플로우를 추적합니다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestResponseLoggingFilter : OncePerRequestFilter() {
    
    private val log = LoggerFactory.getLogger(javaClass)
    
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // 정적 리소스 및 WebSocket 요청은 로깅 제외
        val uri = request.requestURI
        if (shouldSkipLogging(uri)) {
            filterChain.doFilter(request, response)
            return
        }
        
        val method = request.method
        val clientIp = getClientIp(request)
        val startTime = System.currentTimeMillis()
        
        // 요청 로그
        log.info("[→ REQ] $method $uri from $clientIp")
        
        try {
            filterChain.doFilter(request, response)
        } finally {
            val duration = System.currentTimeMillis() - startTime
            val status = response.status
            
            // 응답 로그
            log.info("[← RES] $method $uri - $status (${duration}ms)")
        }
    }
    
    private fun shouldSkipLogging(uri: String): Boolean {
        return uri.startsWith("/ws") ||
               uri.endsWith(".html") ||
               uri.endsWith(".css") ||
               uri.endsWith(".js") ||
               uri.endsWith(".ico") ||
               uri.endsWith(".png") ||
               uri.endsWith(".jpg") ||
               uri.startsWith("/webjars") ||
               uri.startsWith("/static")
    }
    
    private fun getClientIp(request: HttpServletRequest): String {
        val xForwardedFor = request.getHeader("X-Forwarded-For")
        return if (!xForwardedFor.isNullOrBlank()) {
            xForwardedFor.split(",").first().trim()
        } else {
            request.remoteAddr
        }
    }
}
