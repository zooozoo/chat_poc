# Socket Message Spec

이 문서는 프론트엔드와 백엔드 간의 WebSocket 통신 스펙을 정리한 문서입니다.
**STOMP Protocol (v1.1/1.2)** 기준이며, JSON Payload 명세와 실제 전송되는 STOMP Frame 예시를 함께 제공합니다.

> **참고**: `Authorization` 헤더는 `CONNECT` 프레임에서만 전송하며, 이후 개별 메시지(`SEND`)에는 별도의 인증 헤더가 필요하지 않습니다.

---

## 1. 채팅방 (User, Admin 공통)

채팅방 내부에서 실시간 대화와 읽음 처리를 위해 사용합니다.

### 1-1. 메시지 (Message)

#### A. 메시지 전송 (Front -> Server)
*   **Destination**: `/app/chat/{roomId}/send`
*   **Payload (JSON)**:
    ```json
    {
      "content": "안녕하세요, 문의드립니다."
    }
    ```

**STOMP Frame Example:**
```stomp
SEND
destination:/app/chat/123/send
content-type:application/json

{"content":"안녕하세요, 문의드립니다."}
^@
```

#### B. 메시지 수신 (Server -> Front)
*   **Topic**: `/topic/chat/{roomId}`
*   **Payload (JSON)**: `ChatMessageResponse`
    ```json
    {
      "id": 101,
      "chatRoomId": 123,
      "senderId": 456,
      "senderType": "USER",  // "USER" or "ADMIN"
      "content": "안녕하세요, 문의드립니다.",
      "createdAt": "2024-01-14 13:30:00"
    }
    ```

**STOMP Frame Example:**
```stomp
MESSAGE
destination:/topic/chat/123
content-type:application/json
message-id:abc-123-def-456
subscription:sub-0

{"id":101,"chatRoomId":123,"senderId":456,"senderType":"USER","content":"안녕하세요, 문의드립니다.","createdAt":"2024-01-14 13:30:00"}
^@
```

### 1-2. 읽음 처리 (Read)

#### A. 읽음 처리 요청 (Front -> Server)
*   **Destination**: `/app/chat/{roomId}/read`
*   **Payload**: 없음 (Empty Body)
    *   *입장 시 또는 포커스 시 전송*

**STOMP Frame Example:**
```stomp
SEND
destination:/app/chat/123/read

^@
```

#### B. 읽음 알림 수신 (Server -> Front)
*   **Topic**: `/topic/chat/{roomId}/read`
*   **Payload (JSON)**: `ReadNotification`
    ```json
    {
      "chatRoomId": 123,
      "readByType": "ADMIN", // 누가 읽었는지 ("USER" or "ADMIN")
      "readAt": "2024-01-14 13:30:05" // 읽음 처리 시각
    }
    ```

**STOMP Frame Example:**
```stomp
MESSAGE
destination:/topic/chat/123/read
content-type:application/json
subscription:sub-1

{"chatRoomId":123,"readByType":"ADMIN","readAt":"2024-01-14 13:30:05"}
^@
```

---

## 2. Admin 채팅방 목록 (Admin Only)

관리자가 채팅방 목록(Sidebar 등)에서 실시간 상태 변화를 감지하기 위해 사용합니다.

### 2-1. 목록 업데이트 (새 메시지 수신 등)

*   **Topic**: `/topic/admin/chatrooms`
*   **Payload (JSON)**: `ChatRoomNotification`
    ```json
    {
      "chatRoomId": 123,
      "userEmail": "user@example.com",
      "unreadCount": 5,          // 갱신된 안 읽은 메시지 수
      "lastMessageContent": "안녕하세요...",
      "lastMessageAt": "2024-01-14 13:30:00",
      "assignedAdminId": null    // 배정된 관리자 ID (없으면 null)
    }
    ```

**STOMP Frame Example:**
```stomp
MESSAGE
destination:/topic/admin/chatrooms
content-type:application/json
subscription:sub-2

{"chatRoomId":123,"userEmail":"user@example.com","unreadCount":5,"lastMessageContent":"안녕하세요...","lastMessageAt":"2024-01-14 13:30:00","assignedAdminId":null}
^@
```

### 2-2. 메시지 읽음 반영

*   **Topic**: `/topic/admin/reads`
*   **Payload (JSON)**: `ReadNotification` (위와 동일)
    ```json
    {
      "chatRoomId": 123,
      "readByType": "USER",
      "readAt": "2024-01-14 13:30:05"
    }
    ```

**STOMP Frame Example:**
```stomp
MESSAGE
destination:/topic/admin/reads
content-type:application/json
subscription:sub-3

{"chatRoomId":123,"readByType":"USER","readAt":"2024-01-14 13:30:05"}
^@
```

### 2-3. 방 배정 실시간 반영

*   **Topic**: `/topic/admin/assignments`
*   **Payload (JSON)**: `ChatRoomAssignmentNotification`
    ```json
    {
      "chatRoomId": 123,
      "assignedAdminId": 999,
      "assignedAdminEmail": "admin@company.com",
      "assignedAt": "2024-01-14 13:35:00"
    }
    ```

**STOMP Frame Example:**
```stomp
MESSAGE
destination:/topic/admin/assignments
content-type:application/json
subscription:sub-4

{"chatRoomId":123,"assignedAdminId":999,"assignedAdminEmail":"admin@company.com","assignedAt":"2024-01-14 13:35:00"}
^@
```
