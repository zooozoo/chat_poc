package com.chat.poc

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ChatPocApplication

fun main(args: Array<String>) {
    runApplication<ChatPocApplication>(*args)
}
