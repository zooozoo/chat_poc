package com.chat.poc.infrastructure.config

import com.chat.poc.infrastructure.jwt.JwtAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(private val jwtAuthenticationFilter: JwtAuthenticationFilter) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
                .csrf { it.disable() }
                .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
                .authorizeHttpRequests { auth ->
                    auth
                            // 로그인 API는 인증 불필요
                            .requestMatchers("/api/users/login", "/api/admins/login")
                            .permitAll()
                            // WebSocket 엔드포인트
                            .requestMatchers("/ws/**")
                            .permitAll()
                            // 정적 리소스
                            .requestMatchers("/*.html", "/css/**", "/js/**", "/index.html", "/")
                            .permitAll()
                            // 그 외 모든 요청은 인증 필요
                            .anyRequest()
                            .authenticated()
                }
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter::class.java
                )

        return http.build()
    }
}
