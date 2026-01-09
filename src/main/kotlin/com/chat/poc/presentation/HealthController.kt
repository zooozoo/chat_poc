package com.chat.poc.presentation

import java.time.LocalDateTime
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController {

    @GetMapping("/health")
    fun health(): Map<String, Any> {
        return mapOf("status" to "UP", "timestamp" to LocalDateTime.now().toString())
    }
}
