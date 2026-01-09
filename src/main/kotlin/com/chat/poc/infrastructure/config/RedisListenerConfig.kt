package com.chat.poc.infrastructure.config

import com.chat.poc.infrastructure.redis.RedisPublisher
import com.chat.poc.infrastructure.redis.RedisSubscriber
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.PatternTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer

@Configuration
class RedisListenerConfig(private val redisSubscriber: RedisSubscriber) {

    @Bean
    fun redisMessageListenerContainer(
            connectionFactory: RedisConnectionFactory
    ): RedisMessageListenerContainer {
        return RedisMessageListenerContainer().apply {
            setConnectionFactory(connectionFactory)

            // 채팅방 메시지 구독 (패턴 매칭)
            addMessageListener(
                    redisSubscriber,
                    PatternTopic("${RedisPublisher.CHAT_CHANNEL_PREFIX}*")
            )

            // Admin 알림 채널 구독
            addMessageListener(
                    redisSubscriber,
                    ChannelTopic(RedisPublisher.ADMIN_NOTIFICATION_CHANNEL)
            )

            // 읽음 알림 채널 구독 (패턴 매칭)
            addMessageListener(
                    redisSubscriber,
                    PatternTopic("${RedisPublisher.READ_NOTIFICATION_PREFIX}*")
            )
        }
    }
}
