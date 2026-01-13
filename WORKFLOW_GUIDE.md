# Chat POC - Backend Developer Workflow Guide

## 1. 개요
이 문서는 Chat POC 프로젝트의 핵심 기능에 대한 서버 내부 동작 흐름을 설명합니다. 
WebSocket 연결, Redis Pub/Sub 메시징, 읽음 처리 로직 등 백엔드 개발자가 파악해야 할 상세 흐름을 **Mermaid Sequence Diagram**과 함께 기술했습니다.
특히 WebSocket 통신 구간에서는 **STOMP 프로토콜**의 상세 프레임 구조를 포함하여 패킷 수준의 이해를 돕습니다.

---

## 2. 인증 및 세션 (Authentication Flow)
단순화를 위해 Spring Session과 HttpSession을 사용하며, 클라이언트(Browser)는 기본 쿠키(`JSESSIONID`) 기반으로 인증을 유지합니다.

### 2.1 로그인 및 세션 생성
User가 이메일 입력 시 DB에 없으면 자동 생성 후 세션을 발급합니다.

```mermaid
sequenceDiagram
    participant C as Client
    participant API as AuthController
    participant S as AuthService
    participant DB as MySQL
    participant Sess as HttpSession

    C->>API: POST /api/{users|admins}/login (email)
    API->>S: login(email)
    S->>DB: findByEmail(email)
    alt User Not Found
        S->>DB: save(newUser)
    end
    S->>Sess: setAttribute("userId", id)
    S->>Sess: setAttribute("userType", TYPE)
    S-->>C: Cookie: JSESSIONID
```

#### ✉️ HTTP Request Spec

**POST** `/api/users/login` (User 로그인)
**POST** `/api/admins/login` (Admin 로그인)

**Request Body**:
```json
{
  "email": "admin1@email.com"
}
```

**Response**: `LoginResponse`
```json
{
  "success": true,
  "data": {
    "id": 5,
    "email": "admin1@email.com",
    "userType": "ADMIN"
  }
}
```
- **userType**: "USER" 또는 "ADMIN"
- 이메일이 DB에 없으면 자동으로 생성 후 로그인 처리
- 세션에 `userId`, `userType`이 저장되고, `JSESSIONID` 쿠키 발급

---

## 3. 웹소켓 연결 및 핸드셰이크 (WebSocket Handshake)
HTTP 세션에 저장된 사용자 정보를 WebSocket 세션으로 이관하는 과정이 필수적입니다.
`WebSocketHandshakeInterceptor`가 이 역할을 수행합니다.

```mermaid
sequenceDiagram
    participant C as Client (SockJS)
    participant WS as WebSocketHandler
    participant I as HandshakeInterceptor
    participant Sess as HttpSession

    C->>WS: GET /ws (Upgrade Request)
    WS->>I: beforeHandshake()
    I->>Sess: getAttribute("userId", "userType")
    Sess-->>I: {userId: 1, userType: "USER"}
    I->>I: attributes.put("userId", 1)
    WS-->>C: 101 Switching Protocols
    Note right of C: WebSocket Connection Established
    
    C->>WS: STOMP CONNECT
    WS-->>C: STOMP CONNECTED
```

#### ✉️ STOMP Frame: CONNECT
```text
CONNECT
accept-version:1.1,1.0
heart-beat:10000,10000

^@
```

#### ✉️ STOMP Frame: CONNECTED
```text
CONNECTED
version:1.1
heart-beat:0,0
user-name:user1@email.com

^@
```

---

## 4. 실시간 메시지 전송 (Real-time Messaging Flow)
STOMP 프로토콜을 사용하여 메시지를 전송하고, **Redis Pub/Sub**을 통해 다중 서버 환경(확장 고려)에서도 메시지가 전파되도록 설계되었습니다.

### 4.1 메시지 전송 및 저장
```mermaid
sequenceDiagram
    participant Sender as User
    participant Socket as Connection
    participant C as ChatWebSocketController
    participant S as MessageService
    participant DB as MySQL
    participant Pub as RedisPublisher
    participant Redis as Redis Channel

    Sender->>Socket: SEND /app/chat/{roomId}/send
    Socket->>C: sendMessage(payload, headerAccessor)
    C->>C: Extract userId from Session
    C->>S: sendMessage(roomId, userId, content)
    S->>DB: save(Message)
    S->>DB: update ChatRoom (lastMessage, unreadCount)
    
    par Real-time Broadcast
        S->>Pub: publishMessage(roomId, messageDto)
        Pub->>Redis: PUBLISH chat:room:{roomId}
    and Admin Notification
        S->>Pub: publishAdminNotification(notification)
        Pub->>Redis: PUBLISH chat:admin:notification
    end
```

#### ✉️ STOMP Frame: SEND (Client → Server)
클라이언트가 대화 내용을 서버로 전송할 때 사용하는 프레임입니다.
```text
SEND
destination:/app/chat/1/send
content-length:45

{"content":"안녕하세요, 문의드립니다."}
^@
```
- **destination**: `/app` prefix는 `@MessageMapping`이 처리합니다.

#### ✉️ Redis Publish (Internal) - Chat Message
서버 내부에서 Redis로 Broadcasting 하는 메시지 페이로드입니다.

**Channel**: `chat:room:1`
```json
{
  "id": 101,
  "chatRoomId": 1,
  "senderId": 10,
  "senderType": "USER",
  "content": "안녕하세요, 문의드립니다.",
  "createdAt": "2026-01-09 16:30:26"
}
```

#### ✉️ Redis Publish (Internal) - Admin Notification
User가 메시지를 보낼 때 Admin에게 채팅방 업데이트 알림을 전송하는 페이로드입니다.

**Channel**: `chat:admin:notification`
```json
{
  "chatRoomId": 1,
  "userEmail": "user1@email.com",
  "unreadCount": 1,
  "lastMessageContent": "안녕하세요, 문의드립니다.",
  "lastMessageAt": "2026-01-09 16:30:26",
  "assignedAdminId": null
}
```
- **assignedAdminId**: 배정된 Admin의 ID (미배정 시 null)

### 4.2 메시지 수신 및 전달 (구독자에게)
Redis Subscriber가 메시지를 수신하여 WebSocket을 통해 접속 중인 클라이언트들에게 전달합니다.

```mermaid
sequenceDiagram
    participant Redis as Redis
    participant Sub as RedisSubscriber
    participant STOMP as SimpMessagingTemplate
    participant Receiver as Client (Subscriber)

    Redis->>Sub: onMessage(channel, payload)
    Sub->>Sub: deserialize(payload)
    
    alt is Chat Message
        Sub->>STOMP: convertAndSend("/topic/chat/{roomId}")
    else is Admin Notification
        Sub->>STOMP: convertAndSend("/topic/admin/chatrooms")
    end
    
    STOMP->>Receiver: MESSAGE frame
```

#### ✉️ STOMP Frame: MESSAGE (Chat Message)
구독 중인 클라이언트에게 전달되는 메시지 프레임입니다.
```text
MESSAGE
destination:/topic/chat/1
content-type:application/json
subscription:sub-0
message-id:nx92k-0

{"id":101,"chatRoomId":1,"senderId":10,"senderType":"USER","content":"안녕하세요","createdAt":"..."}
^@
```

#### ✉️ STOMP Frame: MESSAGE (Admin Notification)
관리자 대시보드 목록 갱신을 위해 전달되는 알림입니다. `assignedAdminId`를 통해 '미배정'/'내 상담' 탭을 구분합니다.
```text
MESSAGE
destination:/topic/admin/chatrooms
content-type:application/json
subscription:sub-admin-0

{
  "chatRoomId": 1,
  "userEmail": "user1@email.com",
  "unreadCount": 1,
  "lastMessageContent": "안녕하세요",
  "lastMessageAt": "2026-01-09 16:30:26",
  "assignedAdminId": null
}
^@
```

---

## 5. 읽음 처리 프로세스 (Read Status Flow)
실시간 WebSocket 기반의 읽음 처리 프로세스입니다. 채팅방 입장 시 또는 메시지 수신 시 클라이언트가 명시적으로 "Read" 명령을 전송하여 읽음 상태를 동기화합니다.

### 5.1 채팅방 입장 (데이터 조회)
채팅방에 입장할 때 메시지 목록과 채팅방 정보를 조회합니다. **읽음 처리는 별도의 WebSocket 명령으로 수행됩니다.**

```mermaid
sequenceDiagram
    participant Client as Client (User/Admin)
    participant API as ChatRoomController
    participant S as ChatRoomService
    participant DB as MySQL

    Client->>API: GET /api/chatrooms/{id}
    API->>S: enterChatRoom(id, isAdmin)
    S->>DB: findMessages()
    S-->>Client: ChatRoomDetail (Messages only)

    Note over Client: 읽음 처리는 WebSocket을 통해 별도로 수행
```

#### ✉️ HTTP Request Spec
**GET** `/api/chatrooms/{id}`
- **Path Parameter**: `id` (채팅방 ID)
- **세션 인증 필요**: User 또는 Admin
- **권한**: User는 본인 채팅방만 접근 가능, Admin은 모든 채팅방 접근 가능
- **동작**: 메시지 목록만 조회하며, 읽음 처리는 수행하지 않음

**Response**: `ChatRoomDetailResponse`
```json
{
  "success": true,
  "data": {
    "id": 1,
    "userEmail": "user1@email.com",
    "assignedAdminEmail": "admin1@email.com",
    "messages": [
      {
        "id": 101,
        "senderId": 10,
        "senderType": "USER",
        "content": "안녕하세요",
        "isRead": false,
        "readAt": null,
        "createdAt": "2026-01-09 16:30:26"
      }
    ],
    "createdAt": "2026-01-09 15:00:00"
  }
}
```

### 5.2 실시간 읽음 처리 (WebSocket Read Command)
클라이언트가 채팅방 입장 시 또는 메시지 수신 시 명시적으로 읽음 명령을 전송합니다. 서버는 상대방이 보낸 메시지를 읽음 처리하고 Redis를 통해 실시간 알림을 전송합니다.

```mermaid
sequenceDiagram
    participant Client as Client (User/Admin)
    participant WS as ChatWebSocketController
    participant S as ChatRoomService
    participant DB as MySQL
    participant Pub as RedisPublisher
    participant Redis as Redis Channel

    Client->>WS: SEND /app/chat/{roomId}/read
    WS->>WS: Extract userId, userType from Session
    WS->>S: markAsRead(roomId, userId, userType)

    rect rgb(240, 248, 255)
        Note right of S: Mark Opposite Side Messages as Read
        S->>DB: markAllAsRead(chatRoomId, senderTypeToMarkRead)
    end

    alt if markedCount > 0
        S->>Pub: publishReadNotification(roomId, ReadNotification)
        Pub->>Redis: PUBLISH chat:read:{roomId}
    end

    S-->>Client: (Async completion)
```

#### ✉️ STOMP Frame: SEND (Read Command)
클라이언트가 읽음 처리를 요청할 때 전송하는 프레임입니다.
```text
SEND
destination:/app/chat/1/read
content-length:0

^@
```
- **destination**: `/app/chat/{roomId}/read`
- **body**: 없음 (빈 메시지)
- **호출 시점**:
  - 채팅방 입장 시 (HTTP API 호출 후)
  - 메시지 수신 시 (실시간 읽음 처리)

#### ✉️ Redis Publish (Internal) - Read Notification
읽음 처리가 발생하면 상대방에게 알림을 전송하는 페이로드입니다.

**Channel**: `chat:read:{roomId}`
```json
{
  "chatRoomId": 1,
  "readByType": "ADMIN",
  "readAt": "2026-01-09 17:00:00"
}
```
- **readByType**: 누가 읽었는지 ("USER" 또는 "ADMIN")

### 5.3 읽음 알림 전달 (실시간 업데이트)
상대방이 메시지를 읽었음(입장함)을 실시간으로 내 화면에 반영합니다.
읽음 처리 시 **두 가지 경로**로 알림이 전송됩니다:
1. **채팅방 내부** - 상대방에게 읽음 상태 전달 (`/topic/chat/{roomId}/read`)
2. **Admin 목록 화면** - 모든 Admin의 채팅방 목록 뱃지 업데이트 (`/topic/admin/reads`)

```mermaid
sequenceDiagram
    participant Redis as Redis
    participant Sub as RedisSubscriber
    participant STOMP as SimpMessagingTemplate
    participant User as User Client
    participant AdminList as Admin List (All)

    Redis->>Sub: onMessage("chat:read:{roomId}")

    par 채팅방 내부 알림
        Sub->>STOMP: convertAndSend("/topic/chat/{roomId}/read")
        STOMP-->>User: MESSAGE (ReadNotification)
        User->>User: UI Update (Change 'Unread' to 'Read')
    and Admin 목록 동기화
        Sub->>STOMP: convertAndSend("/topic/admin/reads")
        STOMP-->>AdminList: MESSAGE (ReadNotification)
        AdminList->>AdminList: Update Badge to 0
    end
```

#### ✉️ STOMP Frame: MESSAGE (Read Notification - 채팅방 내부)
채팅방에 입장한 상대방에게 전달되는 읽음 알림입니다.
```text
MESSAGE
destination:/topic/chat/1/read
content-type:application/json
subscription:sub-1
message-id:nx92k-1

{"chatRoomId":1,"readByType":"ADMIN","readAt":"2026-01-09 17:00:00"}
^@
```

#### ✉️ STOMP Frame: MESSAGE (Read Notification - Admin 목록)
모든 Admin의 채팅방 목록 화면에 브로드캐스트되는 읽음 알림입니다.

**목적**: Admin이 채팅방에 입장하여 메시지를 읽으면, 다른 Admin(또는 같은 Admin의 다른 브라우저 탭)의 채팅방 목록에서 해당 방의 미읽음 뱃지를 실시간으로 0으로 업데이트합니다.

**시나리오 예시**:
- Admin1이 "미배정" 탭에서 채팅방 목록을 보고 있음 (채팅방 ID 1의 뱃지: 5개 미읽음)
- Admin1이 다른 브라우저 탭 또는 Admin2가 채팅방 1에 입장하여 메시지를 읽음
- "미배정" 탭의 채팅방 1 뱃지가 실시간으로 0으로 변경됨 (페이지 새로고침 불필요)

```text
MESSAGE
destination:/topic/admin/reads
content-type:application/json
subscription:sub-admin-reads

{"chatRoomId":1,"readByType":"ADMIN","readAt":"2026-01-09 17:00:00"}
^@
```

**Backend 구현** (RedisSubscriber.kt:66-77):
```kotlin
private fun handleReadNotification(channel: String, payload: String) {
    val chatRoomId = channel.removePrefix(RedisPublisher.READ_NOTIFICATION_PREFIX)
    val notification = objectMapper.readValue(payload, ReadNotification::class.java)

    // 1. 채팅방 내부 사용자에게 알림
    messagingTemplate.convertAndSend("/topic/chat/$chatRoomId/read", notification)

    // 2. 관리자 목록(Sidebar) 업데이트용 알림
    messagingTemplate.convertAndSend("/topic/admin/reads", notification)
}
```

**Frontend 구독 및 처리** (admin.html:318-335):
```javascript
// 읽음 알림(목록 갱신용) 구독
stompClient.subscribe('/topic/admin/reads', (message) => {
    const noti = JSON.parse(message.body);
    handleReadNotificationForList(noti);
});

function handleReadNotificationForList(noti) {
    // Admin이 읽었으므로 뱃지를 0으로 변경
    if (noti.readByType === 'ADMIN') {
        const badge = document.getElementById(`badge-${noti.chatRoomId}`);
        if (badge) {
            badge.textContent = '0';
            badge.classList.add('hidden');
        }
    }
}
```

---

## 6. 상담사 배정 (Assignment Flow)
관리자가 미배정 채팅방을 담당자로 배정받는 과정입니다. 다른 관리자들의 화면에서도 해당 방이 '미배정' 목록에서 사라지도록 동기화해야 합니다.

### 6.1 미배정/내 상담 목록 조회
관리자는 채팅방 목록을 필터링하여 조회할 수 있습니다.

```mermaid
sequenceDiagram
    participant Admin as Admin Client
    participant API as AdminAssignmentController
    participant S as ChatRoomService
    participant DB as MySQL

    par 미배정 목록 조회
        Admin->>API: GET /api/admins/chatrooms/unassigned
        API->>S: getUnassignedChatRooms()
        S->>DB: findAllByAdminIsNull()
        DB-->>S: List<ChatRoom> (admin = null)
        S-->>Admin: ChatRoomListResponse
    and 내 담당 목록 조회
        Admin->>API: GET /api/admins/chatrooms/mine
        API->>S: getMyChatRooms(adminId)
        S->>DB: findAllByAdmin(admin)
        DB-->>S: List<ChatRoom> (admin = adminId)
        S-->>Admin: ChatRoomListResponse
    end
```

#### ✉️ HTTP Request Spec
**GET** `/api/admins/chatrooms/unassigned`
- **세션 인증 필요**: Admin
- **응답**: `ChatRoomListResponse` (admin이 null인 채팅방 목록)

**GET** `/api/admins/chatrooms/mine`
- **세션 인증 필요**: Admin
- **응답**: `ChatRoomListResponse` (현재 로그인한 Admin이 배정된 채팅방 목록)

**Response**: `ChatRoomListResponse`
```json
{
  "success": true,
  "data": {
    "chatRooms": [
      {
        "id": 1,
        "userId": 10,
        "userEmail": "user1@email.com",
        "unreadCount": 3,
        "lastMessageContent": "문의드립니다",
        "lastMessageAt": "2026-01-09 16:30:26",
        "assignedAdminEmail": null,
        "createdAt": "2026-01-09 15:00:00"
      },
      {
        "id": 2,
        "userId": 11,
        "userEmail": "user2@email.com",
        "unreadCount": 0,
        "lastMessageContent": "감사합니다",
        "lastMessageAt": "2026-01-09 14:20:15",
        "assignedAdminEmail": "admin1@email.com",
        "createdAt": "2026-01-09 14:00:00"
      }
    ]
  }
}
```
- **unreadCount**: User가 보낸 읽지 않은 메시지 수
- **assignedAdminEmail**: 배정된 Admin 이메일 (미배정 시 null)

### 6.2 배정 요청 및 알림
```mermaid
sequenceDiagram
    participant AdminA as Admin A (Assigner)
    participant API as AdminAssignmentController
    participant S as ChatRoomService
    participant DB as MySQL
    participant Pub as RedisPublisher
    participant Redis as Redis Channel
    participant OtherAdmins as Other Admins

    AdminA->>API: POST /api/admins/chatrooms/{id}/assign
    API->>S: assignChatRoom(roomId, adminId)
    
    S->>DB: findById(roomId)
    S->>S: Check if already assigned
    S->>DB: update ChatRoom (set admin = adminId)
    
    rect rgb(255, 240, 245)
        Note right of S: Real-time Sync
        S->>Pub: publishAssignmentNotification
        Pub->>Redis: PUBLISH chat:admin:assignment
    end
    
    Redis->>OtherAdmins: Subscribe & Alert
```

#### ✉️ HTTP Request Spec
**POST** `/api/admins/chatrooms/{id}/assign`
- **Path Parameter**: `id` (채팅방 ID)
- **세션 인증 필요**: Admin
- **동작**: 현재 로그인한 Admin을 해당 채팅방의 담당자로 배정

**Request Body**: 없음

**Response**: `ApiResponse<Unit>`
```json
{
  "success": true,
  "data": null
}
```

**Error Cases**:
- 이미 배정된 채팅방: `400 Bad Request`
- 채팅방 미존재: `404 Not Found`
- Admin 미인증: `401 Unauthorized`

### 6.3 배정 알림 수신 (For Sync)
Redis Subscriber가 배정 알림을 수신하여 `/topic/admin/assignments`를 구독 중인 모든 관리자에게 브로드캐스팅합니다.

#### ✉️ Redis Publish Payload

**Channel**: `chat:admin:assignment`
```json
{
  "chatRoomId": 1,
  "assignedAdminId": 5,
  "assignedAdminEmail": "admin1@email.com",
  "assignedAt": "2026-01-09 18:30:00"
}
```

#### ✉️ STOMP Frame: MESSAGE (Assignment Notification)
관리자 클라이언트는 이 메시지를 받으면:
1. 배정자가 **자신**이면, '미배정' 탭에서 방을 제거하고 '내 상담' 및 '목록'을 갱신합니다.
2. 배정자가 **타인**이면, '미배정' 탭에서 방을 즉시 제거합니다.

```text
MESSAGE
destination:/topic/admin/assignments
content-type:application/json
subscription:sub-admin-noti

{"chatRoomId":1,"assignedAdminId":5,"assignedAdminEmail":"admin1@email.com","assignedAt":"..."}
^@
```

---

## 7. 메시지 페이지네이션 조회 (Message Pagination)
채팅방 입장 시 전체 메시지가 아닌 페이지 단위로 메시지를 조회할 수 있는 기능입니다. 무한 스크롤 또는 더보기 기능 구현에 활용됩니다.

### 7.1 페이지네이션 조회 프로세스
```mermaid
sequenceDiagram
    participant Client as Client (User/Admin)
    participant API as ChatRoomController
    participant S as ChatRoomService
    participant DB as MySQL

    Client->>API: GET /api/chatrooms/{id}/messages?page=0&size=20
    API->>API: 세션 인증 확인
    API->>S: getMessages(id, page, size)
    S->>DB: findByChatRoomIdOrderByCreatedAtDesc(id, pageable)
    DB-->>S: Page<Message>
    S-->>Client: MessageListResponse

    Note over Client: {messages, page, totalPages, hasNext}
```

#### ✉️ HTTP Request Spec
**GET** `/api/chatrooms/{id}/messages`

**Query Parameters**:
- `page` (optional, default=0): 페이지 번호 (0부터 시작)
- `size` (optional, default=20): 페이지당 메시지 수

**Response**: `MessageListResponse`
```json
{
  "success": true,
  "data": {
    "messages": [
      {
        "id": 101,
        "senderId": 10,
        "senderType": "USER",
        "content": "안녕하세요",
        "isRead": true,
        "readAt": "2026-01-09 17:00:00",
        "createdAt": "2026-01-09 16:30:26"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 45,
    "totalPages": 3,
    "hasNext": true
  }
}
```

**Note**:
- 메시지는 최신순(내림차순)으로 정렬되어 반환됩니다.
- User는 본인의 채팅방만 조회 가능하며, Admin은 모든 채팅방 조회 가능합니다.
- 권한이 없는 경우 403 Forbidden을 반환합니다.

---

## 8. WebSocket 구독 채널 및 실시간 통신 흐름 (Real-time Communication Flow)
이 섹션에서는 User와 Admin 화면에서 WebSocket을 통해 어떤 채널을 구독하고, 어떤 메시지를 전송하는지 전체적으로 정리합니다. 실시간 동기화가 어떻게 이루어지는지 시나리오별로 이해할 수 있습니다.

### 8.1 WebSocket 연결 개요
Chat POC는 **STOMP over WebSocket** 프로토콜을 사용하여 실시간 양방향 통신을 구현합니다.

#### 연결 엔드포인트
- **URL**: `/ws`
- **프로토콜**: STOMP 1.1/1.0
- **Fallback**: SockJS (WebSocket 미지원 환경)
- **인증**: HTTP 세션 기반 (WebSocketHandshakeInterceptor를 통해 WebSocket 세션으로 전달)

#### 메시지 브로커 구조

| 방향 | Prefix | 용도 | 예시 | 처리 위치 |
|------|--------|------|------|-----------|
| Client → Server | `/app` | 메시지 전송, 읽음 처리 요청 | `/app/chat/1/send` | ChatWebSocketController |
| Server → Client | `/topic` | 실시간 알림, 브로드캐스트 | `/topic/chat/1` | RedisSubscriber → SimpMessagingTemplate |

**주요 특징**:
- `/app` prefix는 `@MessageMapping`으로 매핑되어 서버 비즈니스 로직을 실행합니다.
- `/topic` prefix는 Pub/Sub 패턴으로 다수의 클라이언트에게 동시에 메시지를 전달합니다.
- Redis Pub/Sub를 통해 다중 서버 환경에서도 실시간 메시지가 모든 인스턴스로 전파됩니다.

---

### 8.2 User 화면 WebSocket 통신
User는 채팅방에 입장할 때 WebSocket에 연결하고, 2개의 채널을 구독합니다.

#### 연결 시점
**위치**: `user.html:90-126`
- 채팅방 조회 API (`GET /api/users/chatroom`) 호출 후 WebSocket 연결
- 채팅방 ID를 받아 연결 및 구독 수행

#### 구독 채널 (2개)

| 채널 | 목적 | 수신 시 처리 | 코드 위치 |
|------|------|-------------|-----------|
| `/topic/chat/{roomId}` | 실시간 채팅 메시지 수신 | 메시지 화면에 추가, 자동 스크롤, 자동 읽음 처리 | user.html:106 |
| `/topic/chat/{roomId}/read` | 상대방(Admin) 읽음 알림 | 내가 보낸 메시지에 "읽음" 표시 | user.html:119 |

**구독 코드 예시** (user.html:106-121):
```javascript
// 1. 채팅 메시지 구독
stompClient.subscribe(`/topic/chat/${chatRoomId}`, (message) => {
    const msg = JSON.parse(message.body);
    displayMessage(msg);
    scrollToBottom();

    // 메시지 수신 시 읽음 처리
    stompClient.send(`/app/chat/${chatRoomId}/read`, {}, {});
});

// 2. 읽음 알림 구독
stompClient.subscribe(`/topic/chat/${chatRoomId}/read`, (message) => {
    markAllAsRead();
});
```

#### 전송 엔드포인트 (2개)

| Destination | Payload | 트리거 | 코드 위치 |
|-------------|---------|--------|-----------|
| `/app/chat/{roomId}/send` | `{"content": "메시지 내용"}` | 전송 버튼 클릭 또는 Enter 키 | user.html:137 |
| `/app/chat/{roomId}/read` | 없음 (빈 메시지) | 채팅방 입장 시, 메시지 수신 시 | user.html:112, 116 |

**전송 코드 예시** (user.html:137-139):
```javascript
// 메시지 전송
stompClient.send(`/app/chat/${chatRoomId}/send`, {}, JSON.stringify({
    content: content
}));
```

**읽음 처리 코드** (user.html:112, 116):
```javascript
// 입장 시 읽음 처리
stompClient.send(`/app/chat/${chatRoomId}/read`, {}, {});

// 메시지 수신 시 자동 읽음 처리
stompClient.send(`/app/chat/${chatRoomId}/read`, {}, {});
```

#### WebSocket 통신 흐름도

```mermaid
sequenceDiagram
    participant User as User Client
    participant WS as WebSocket (/ws)
    participant Server as ChatWebSocketController
    participant Redis as Redis Pub/Sub

    Note over User: 채팅방 입장
    User->>WS: Connect /ws
    WS->>User: CONNECTED

    User->>WS: SUBSCRIBE /topic/chat/{roomId}
    User->>WS: SUBSCRIBE /topic/chat/{roomId}/read

    Note over User: 입장 시 자동 읽음 처리
    User->>Server: SEND /app/chat/{roomId}/read
    Server->>Redis: Publish read notification

    Note over User: 메시지 전송
    User->>Server: SEND /app/chat/{roomId}/send
    Server->>Redis: Publish message
    Redis-->>User: MESSAGE /topic/chat/{roomId}

    Note over User: Admin이 메시지 읽음
    Redis-->>User: MESSAGE /topic/chat/{roomId}/read
    User->>User: markAllAsRead() 실행
```

---

### 8.3 Admin 화면 WebSocket 통신
Admin은 페이지 로드 시 WebSocket에 연결하여 **목록 화면**에서 3개의 채널을 구독하고, **채팅방 입장** 시 추가로 2개의 채널을 구독합니다.

#### 연결 시점
**위치**: `admin.html:290-324`
- 페이지 로드 시 WebSocket 연결
- 채팅방 목록 화면에서 상시 연결 유지
- 채팅방 입장 시 해당 채팅방의 구독 채널 추가

#### 구독 채널 (총 5개)

##### 목록 화면 (3개) - 상시 구독

| 채널 | 목적 | 수신 시 처리 | 코드 위치 |
|------|------|-------------|-----------|
| `/topic/admin/chatrooms` | 새로운 메시지 알림 | 미읽음 수 증가, 최근 메시지 미리보기 갱신 | admin.html:303 |
| `/topic/admin/reads` | 읽음 상태 동기화 | 미읽음 뱃지를 0으로 변경 | admin.html:318 |
| `/topic/admin/assignments` | 배정 알림 | "내 상담" 탭에 추가 또는 "미배정" 탭에서 제거 | admin.html:312 |

**목록 화면 구독 코드** (admin.html:303-321):
```javascript
// 1. 새로운 메시지 알림 구독
stompClient.subscribe('/topic/admin/chatrooms', (message) => {
    const noti = JSON.parse(message.body);
    updateChatRoomPreview(noti);
});

// 2. 배정 알림 구독
stompClient.subscribe('/topic/admin/assignments', (message) => {
    const noti = JSON.parse(message.body);
    handleAssignmentNotification(noti);
});

// 3. 읽음 알림(목록 갱신용) 구독
stompClient.subscribe('/topic/admin/reads', (message) => {
    const noti = JSON.parse(message.body);
    handleReadNotificationForList(noti);
});
```

##### 채팅방 내부 (2개) - 채팅방 입장 시 구독

| 채널 | 목적 | 수신 시 처리 | 코드 위치 |
|------|------|-------------|-----------|
| `/topic/chat/{roomId}` | 실시간 채팅 메시지 수신 | 메시지 화면에 추가, 자동 스크롤, 자동 읽음 처리 | admin.html:480 |
| `/topic/chat/{roomId}/read` | 상대방(User) 읽음 알림 | 내가 보낸 메시지에 "읽음" 표시 | admin.html:494 |

**채팅방 내부 구독 코드** (admin.html:480-495):
```javascript
// 메시지 구독 (User와 동일)
messageSubscription = stompClient.subscribe(`/topic/chat/${roomId}`, (message) => {
    const msg = JSON.parse(message.body);
    displayMessage(msg);
    scrollToBottom();

    // 메시지 수신 시 읽음 처리
    stompClient.send(`/app/chat/${roomId}/read`, {}, {});
});

// 읽음 알림 구독 (User와 동일)
readSubscription = stompClient.subscribe(`/topic/chat/${roomId}/read`, (message) => {
    markAllAsRead();
});
```

#### 전송 엔드포인트 (2개) - User와 동일

| Destination | Payload | 트리거 | 코드 위치 |
|-------------|---------|--------|-----------|
| `/app/chat/{roomId}/send` | `{"content": "메시지 내용"}` | 전송 버튼 클릭 또는 Enter 키 | admin.html:559 |
| `/app/chat/{roomId}/read` | 없음 (빈 메시지) | 채팅방 입장 시, 메시지 수신 시 | admin.html:487, 491 |

#### WebSocket 통신 흐름도 (목록 화면)

```mermaid
sequenceDiagram
    participant Admin as Admin Client
    participant WS as WebSocket (/ws)
    participant Redis as Redis Pub/Sub
    participant OtherAdmin as Other Admin

    Note over Admin: 페이지 로드
    Admin->>WS: Connect /ws
    WS->>Admin: CONNECTED

    Note over Admin: 목록 화면 구독
    Admin->>WS: SUBSCRIBE /topic/admin/chatrooms
    Admin->>WS: SUBSCRIBE /topic/admin/reads
    Admin->>WS: SUBSCRIBE /topic/admin/assignments

    Note over Admin: User가 메시지 전송
    Redis-->>Admin: MESSAGE /topic/admin/chatrooms
    Admin->>Admin: updateChatRoomPreview() - 미읽음 수 증가

    Note over OtherAdmin: Other Admin이 메시지 읽음
    Redis-->>Admin: MESSAGE /topic/admin/reads
    Admin->>Admin: 뱃지 0으로 변경

    Note over OtherAdmin: Other Admin이 방 배정
    Redis-->>Admin: MESSAGE /topic/admin/assignments
    Admin->>Admin: handleAssignmentNotification() - 미배정 목록에서 제거
```

#### 구독 채널 비교 (User vs Admin)

| 화면 | User | Admin |
|------|------|-------|
| **목록 화면** | 없음 | `/topic/admin/chatrooms`<br>`/topic/admin/reads`<br>`/topic/admin/assignments` |
| **채팅방 내부** | `/topic/chat/{roomId}`<br>`/topic/chat/{roomId}/read` | `/topic/chat/{roomId}`<br>`/topic/chat/{roomId}/read`<br>(User와 동일) |
| **전송** | `/app/chat/{roomId}/send`<br>`/app/chat/{roomId}/read` | `/app/chat/{roomId}/send`<br>`/app/chat/{roomId}/read`<br>(User와 동일) |

**주요 차이점**:
- Admin은 목록 화면에서 **3개의 추가 채널**을 구독하여 실시간 동기화를 수행합니다.
- 채팅방 내부에서는 User와 Admin의 WebSocket 통신이 **동일**합니다.
- Admin은 WebSocket 연결을 **상시 유지**하며, User는 채팅방 입장 시에만 연결합니다.

---

### 8.4 실시간 동기화 시나리오
실제 사용 시나리오를 통해 WebSocket 메시지 흐름과 각 화면에 미치는 영향을 파악할 수 있습니다.

#### 시나리오 1: User가 메시지 전송

**전제 조건**:
- User가 채팅방에 입장하여 WebSocket 연결됨
- Admin1이 목록 화면을 보고 있음 (WebSocket 연결됨)
- Admin2가 해당 채팅방에 입장하여 대화 중

**흐름**:
1. User가 `/app/chat/1/send` 전송 (ChatWebSocketController.kt:26)
2. Backend가 메시지 저장 (MessageService.kt:43-52)
3. Backend가 Redis 발행 (MessageService.kt:69)
   - `chat:room:1` 채널로 메시지 발행
   - `chat:admin:notification` 채널로 Admin 알림 발행 (MessageService.kt:72-84)
4. RedisSubscriber가 수신하여 브로드캐스트 (RedisSubscriber.kt:53-64)
   - `/topic/chat/1` → User, Admin2에게 메시지 전달
   - `/topic/admin/chatrooms` → Admin1, Admin2 목록 업데이트

**영향 받는 화면**:
- ✅ **User 채팅방**: 내 메시지가 즉시 화면에 표시됨
- ✅ **Admin2 채팅방**: 상대방 메시지가 즉시 표시되고, 자동 읽음 처리 전송
- ✅ **Admin1 목록**: 해당 채팅방의 미읽음 수 증가, 최근 메시지 갱신
- ✅ **Admin2 목록**: 해당 채팅방의 미읽음 수 증가, 최근 메시지 갱신

```mermaid
sequenceDiagram
    participant User as User (채팅방)
    participant Backend as Backend
    participant Redis as Redis
    participant Admin1 as Admin1 (목록)
    participant Admin2 as Admin2 (채팅방)

    User->>Backend: SEND /app/chat/1/send
    Backend->>Redis: Publish chat:room:1
    Backend->>Redis: Publish chat:admin:notification

    par 채팅방 참여자에게 메시지 전달
        Redis-->>User: MESSAGE /topic/chat/1
        Redis-->>Admin2: MESSAGE /topic/chat/1
    and Admin 목록 업데이트
        Redis-->>Admin1: MESSAGE /topic/admin/chatrooms
        Redis-->>Admin2: MESSAGE /topic/admin/chatrooms
    end

    Note over Admin2: 메시지 수신 후 자동 읽음 처리
    Admin2->>Backend: SEND /app/chat/1/read
```

---

#### 시나리오 2: Admin이 메시지 읽음

**전제 조건**:
- User가 채팅방에 있음 (메시지 3개 전송, 미읽음 상태)
- Admin1이 목록 화면을 보고 있음 (해당 채팅방 뱃지: 3)
- Admin2가 해당 채팅방에 입장함

**흐름**:
1. Admin2가 채팅방 입장 시 `/app/chat/1/read` 전송 (admin.html:491)
2. Backend가 읽음 처리 (ChatRoomService.kt:138)
   - SenderType.USER인 메시지를 모두 읽음 처리
3. Backend가 Redis 발행 (ChatRoomService.kt:148)
   - `chat:read:1` 채널로 읽음 알림 발행
4. RedisSubscriber가 수신하여 브로드캐스트 (RedisSubscriber.kt:66-77)
   - `/topic/chat/1/read` → User에게 읽음 알림
   - `/topic/admin/reads` → Admin1, Admin2 목록에 읽음 상태 동기화

**영향 받는 화면**:
- ✅ **User 채팅방**: 내가 보낸 3개 메시지에 "읽음" 표시
- ✅ **Admin2 채팅방**: 변화 없음 (이미 읽음 처리 완료)
- ✅ **Admin1 목록**: 해당 채팅방 뱃지가 3 → 0으로 변경
- ✅ **Admin2 목록** (다른 탭): 해당 채팅방 뱃지가 3 → 0으로 변경

```mermaid
sequenceDiagram
    participant User as User (채팅방)
    participant Admin2 as Admin2 (입장)
    participant Backend as Backend
    participant Redis as Redis
    participant Admin1 as Admin1 (목록)

    Note over Admin2: 채팅방 입장 시 읽음 처리
    Admin2->>Backend: SEND /app/chat/1/read
    Backend->>Backend: markAllAsRead(SenderType.USER)
    Backend->>Redis: Publish chat:read:1

    par 읽음 알림 전달
        Redis-->>User: MESSAGE /topic/chat/1/read
        User->>User: markAllAsRead() - "읽음" 표시
    and Admin 목록 동기화
        Redis-->>Admin1: MESSAGE /topic/admin/reads
        Admin1->>Admin1: 뱃지 0으로 변경
        Redis-->>Admin2: MESSAGE /topic/admin/reads
        Admin2->>Admin2: 뱃지 0으로 변경 (다른 탭)
    end
```

---

#### 시나리오 3: 채팅방 배정

**전제 조건**:
- 미배정 채팅방이 존재함 (채팅방 ID: 1)
- Admin1이 "미배정" 탭을 보고 있음
- Admin2가 "미배정" 탭을 보고 있음

**흐름**:
1. Admin1이 `POST /api/admins/chatrooms/1/assign` 호출 (AdminAssignmentController.kt:31)
2. Backend가 배정 처리 (ChatRoomService.kt:194-210)
   - chatRoom.admin = Admin1로 설정
3. Backend가 Redis 발행 (ChatRoomService.kt:222)
   - `chat:admin:assignment` 채널로 배정 알림 발행
4. RedisSubscriber가 수신하여 브로드캐스트 (RedisSubscriber.kt:79-84)
   - `/topic/admin/assignments` → 모든 Admin에게 배정 알림

**영향 받는 화면**:
- ✅ **Admin1 (자신)**: "내 상담" 탭에 추가 (또는 목록 새로고침)
- ✅ **Admin2 (타인)**: "미배정" 탭에서 즉시 제거

```mermaid
sequenceDiagram
    participant Admin1 as Admin1 (미배정 탭)
    participant Backend as Backend
    participant Redis as Redis
    participant Admin2 as Admin2 (미배정 탭)

    Note over Admin1: 배정 버튼 클릭
    Admin1->>Backend: POST /api/admins/chatrooms/1/assign
    Backend->>Backend: chatRoom.admin = Admin1
    Backend->>Redis: Publish chat:admin:assignment

    par 배정 알림 전달
        Redis-->>Admin1: MESSAGE /topic/admin/assignments
        Admin1->>Admin1: handleAssignmentNotification()
        Admin1->>Admin1: "내 상담" 탭에 추가
    and
        Redis-->>Admin2: MESSAGE /topic/admin/assignments
        Admin2->>Admin2: handleAssignmentNotification()
        Admin2->>Admin2: "미배정" 탭에서 제거
    end
```

---

#### 시나리오 4: User와 Admin이 동시에 채팅

**전제 조건**:
- User가 채팅방에 입장함
- Admin이 채팅방에 입장함
- 두 사람이 실시간으로 대화 중

**흐름**:
1. **User 메시지 전송**
   - User → `/app/chat/1/send`
   - Backend → Redis → `/topic/chat/1`
   - Admin 메시지 수신 → 자동 읽음 처리 → `/app/chat/1/read`
   - Backend → Redis → `/topic/chat/1/read`, `/topic/admin/reads`
   - User가 "읽음" 표시 확인

2. **Admin 응답 전송**
   - Admin → `/app/chat/1/send`
   - Backend → Redis → `/topic/chat/1`
   - User 메시지 수신 → 자동 읽음 처리 → `/app/chat/1/read`
   - Backend → Redis → `/topic/chat/1/read`, `/topic/admin/reads`
   - Admin이 "읽음" 표시 확인

**실시간 동기화 효과**:
- ✅ 양방향 메시지가 **즉시 전달**됨 (지연 없음)
- ✅ 읽음 상태가 **실시간으로 반영**됨
- ✅ Admin 목록의 미읽음 수가 **실시간으로 업데이트**됨
- ✅ 다른 Admin이 보는 목록도 **동시에 동기화**됨

```mermaid
sequenceDiagram
    participant User as User
    participant Backend as Backend
    participant Redis as Redis
    participant Admin as Admin
    participant AdminList as Admin (목록-다른 탭)

    rect rgb(240, 248, 255)
        Note over User,AdminList: 1. User 메시지 전송
        User->>Backend: SEND /app/chat/1/send
        Backend->>Redis: Publish message

        par 메시지 전달
            Redis-->>User: /topic/chat/1
            Redis-->>Admin: /topic/chat/1
        and 목록 업데이트
            Redis-->>AdminList: /topic/admin/chatrooms (미읽음 수 증가)
        end

        Admin->>Backend: SEND /app/chat/1/read (자동)
        Backend->>Redis: Publish read notification

        par 읽음 알림
            Redis-->>User: /topic/chat/1/read (읽음 표시)
        and
            Redis-->>AdminList: /topic/admin/reads (뱃지 0)
        end
    end

    rect rgb(255, 248, 240)
        Note over User,AdminList: 2. Admin 응답 전송
        Admin->>Backend: SEND /app/chat/1/send
        Backend->>Redis: Publish message

        Redis-->>User: /topic/chat/1
        Redis-->>Admin: /topic/chat/1

        User->>Backend: SEND /app/chat/1/read (자동)
        Backend->>Redis: Publish read notification

        Redis-->>Admin: /topic/chat/1/read (읽음 표시)
    end

    Note over User,AdminList: 실시간 양방향 통신 완료
```

---

#### 시나리오 요약표

| 시나리오 | 트리거 | 발행 채널 | 영향 받는 화면 |
|----------|--------|-----------|---------------|
| **User 메시지 전송** | User가 메시지 전송 | `chat:room:{id}`<br>`chat:admin:notification` | User 채팅방, Admin 채팅방, Admin 목록 (모든 Admin) |
| **Admin 메시지 읽음** | Admin이 채팅방 입장 | `chat:read:{id}` | User 채팅방 (읽음 표시), Admin 목록 (모든 Admin, 뱃지 0) |
| **채팅방 배정** | Admin이 배정 버튼 클릭 | `chat:admin:assignment` | Admin1 목록 ("내 상담" 추가), Admin2 목록 ("미배정" 제거) |
| **실시간 대화** | User ↔ Admin 메시지 교환 | 위 모든 채널 조합 | 모든 화면 실시간 동기화 |
