# Offline Push Notification Strategy (Distributed Environment)

## 1. 문제 정의
*   **환경**: 다중 웹 서버(Multi-Instance) + Redis 1대
*   **목표**: 수신자(User)가 현재 접속 중이면 **실시간 소켓 전송**, 접속하지 않았다면 **Push 알림(FCM 등) 발송**.
*   **난관**: 웹 서버 A에서 메시지를 보낼 때, 수신자가 웹 서버 B에 붙어있을 수도 있고, 아예 없을 수도 있음. 단순 `SimpMessagingTemplate` 전송만으로는 수신 여부를 알기 어려움(Fire-and-Forget).

---

## 2. 해결 방안: Redis Global Presence (전역 접속 상태 관리)

Redis를 **"현재 접속 중인 사용자 명부(Registry)"**로 활용하는 것이 가장 일반적이고 효과적인 패턴입니다.

### Architecture
1.  **접속 상태 저장 (Presence Management)**
    *   모든 웹 서버는 WebSocket 연결/해제 이벤트를 감지하여 Redis에 상태를 동기화합니다.
    *   **Key**: `USER_ONLINE:{userId}`
    *   **Value**: `ServerId` (또는 Timestamp)
    *   **TTL**: Heartbeat 주기보다 조금 길게 설정 (예: 60초)하여 비정상 종료 시 자동 만료 처리.

2.  **메시지 라우팅 로직 (Message Routing)**
    *   메시지 전송 서비스는 **보내기 전에 Redis를 조회**합니다.
    *   **IF (Key Exists)** -> `convertAndSend()` (소켓 전송)
    *   **ELSE** -> `sendPushNotification()` (푸시 전송)

---

## 3. 구현 상세 (Implementation Details)

### Step A: Presence Listener (상태 동기화)
Spring의 `SessionConnectedEvent`와 `SessionDisconnectEvent`를 활용합니다.

```kotlin
@Component
class WebSocketEventListener(
    private val redisTemplate: StringRedisTemplate
) {
    // 연결 시 Redis에 등록
    @EventListener
    fun handleWebSocketConnectListener(event: SessionConnectedEvent) {
        val user = event.user // Principal에서 userId 추출
        redisTemplate.opsForValue().set("USER_ONLINE:$user", "ON", Duration.ofMinutes(1))
    }

    // 연결 해제 시 Redis에서 삭제
    @EventListener
    fun handleWebSocketDisconnectListener(event: SessionDisconnectEvent) {
        val user = event.user
        redisTemplate.delete("USER_ONLINE:$user")
    }
}
```
*Tip: 안전성을 위해 Heartbeat 때마다 TTL을 갱신해주는 로직을 추가하기도 합니다.*

### Step B: Service Logic (조건부 전송)

```kotlin
fun sendMessage(receiverId: Long, content: String) {
    val isOnline = redisTemplate.hasKey("USER_ONLINE:$receiverId")

    if (isOnline) {
        // 1. 소켓 전송 (Redis Pub/Sub을 통해 해당 유저가 붙은 서버로 전달됨)
        messagingTemplate.convertAndSend("/topic/chat/$roomId", message)
    } else {
        // 2. 오프라인 -> Push 알림 전송
        fcmService.sendPush(receiverId, content)
    }
}
```

---

## 4. Edge Case 고려사항

1.  **Race Condition (타이밍 이슈)**
    *   상황: Redis에는 있다고 나왔는데, 그 0.1초 사이에 유저가 소켓을 끊음.
    *   결과: 메시지는 소켓 채널로 증발(Lost)하고 Push도 안 감.
    *   **해결**: 완벽한 보장을 위해서는 `ACK` 시스템이 필요하지만, 일반적인 채팅에서는 **"소켓 전송 후 Redis TTL 연장"**이나 **"Client가 메시지 수신 시 Read API 호출 -> 안 부르면 일정 시간 후 Push"** 같은 보정 로직을 씁니다. (MVP 단계에서는 무시 가능)

2.  **다중 기기 접속 (Multi-Device)**
    *   모바일과 PC 동시 접속 시, 하나라도 온라인이면 소켓으로 감. 필요하다면 "모두에게 Push는 안 보냄" 또는 "Background인 기기엔 Push 보냄" 등으로 정책 세분화 필요.

---

## 5. 요약
> **"Redis를 거대한 전광판으로 써라."**
> 소켓 연결 시 이름을 적고, 나갈 때 지웁니다. 메시지 보내기 전에 이름이 있는지 확인합니다.
