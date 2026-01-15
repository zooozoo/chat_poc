# WebSocket Authentication Current Status & Common Pattern

## 1. 현재 구현 평가 (Current Implementation Review)

현재 프로젝트는 **STOMP CONNECT Frame Header 방식**을 사용하고 있습니다.

### 구현 상세
*   **방식**: 클라이언트가 소켓 연결 후, STOMP 프로토콜의 `CONNECT` 프레임을 보낼 때 `Authorization` 헤더에 JWT를 실어 보냅니다.
*   **검증**: `JwtChannelInterceptor`에서 `CONNECT` 커맨드일 경우에만 토큰을 검증합니다.

### Security Assessment (보안 평가)
| 항목 | 평가 | 설명 |
| :--- | :--- | :--- |
| **기밀성 (Confidentiality)** | ✅ 우수 | URL(Query String)에 토큰이 노출되지 않아 로그에 남지 않음. |
| **성능 (Performance)** | ✅ 우수 | 연결 수립 시 1회만, 검증하므로 메시지 송수신 부하가 적음. |
| **만료 처리 (Expiration)** | ⚠️ **취약** | **가장 큰 약점.** 연결된 상태에서 토큰이 만료되어도, 소켓 연결은 끊어지지 않고 계속 통신 가능. |
| **호환성 (Compatibility)** | ⚠️ 보통 | 브라우저 기본 WebSocket API는 헤더 조작이 불가능하여, SockJS/StompJS 라이브러리 의존 필수. |

> **종합 의견**:  
> 현재 방식인 **Header-based Authentication**은 SPA(React, Vue 등)와 STOMP를 사용하는 환경에서 **표준적이고 권장되는 방식** 중 하나입니다. 다만, 보안 수준을 높이려면 **토큰 만료 시 강제 연결 종료** 메커니즘을 추가하는 것이 좋습니다.

---

## 2. WebSocket 인증 방식 비교 (Common Patterns)

WebSocket은 HTTP 핸드셰이크로 시작하지만, 표준 `WebSocket` API는 헤더 커스터마이징을 막아두었기 때문에 여러 우회 전략이 사용됩니다.

### A. Ticket-based Authentication (권장 - Best Practice)
가장 보안성이 높고 유연한 방식입니다.
1.  **API 요청**: 클라이언트가 REST API (`POST /auth/ticket`)를 통해 인증 후 "일회용 티켓(Short-lived Ticket)" 발급.
2.  **소켓 연결**: `ws://example.com/socket?ticket={unique_ticket_id}` 로 연결.
3.  **검증**: 서버는 티켓 저장소(Redis 등)에서 티켓을 확인하고 즉시 파기(One-time use).

*   **장점**: 토큰 노출 최소화, 티켓 만료 시간 짧게 설정 가능, 모든 클라이언트 호환.
*   **단점**: 구현 복잡도 증가 (티켓 관리 로직 필요).

### B. Query Parameter with JWT
가장 구현하기 쉬운 방식입니다.
*   **방식**: `ws://example.com/socket?token={jwt}`
*   **장점**: 브라우저 기본 API 사용 가능.
*   **단점**: **보안 취약.** 서버 로그, 프록시 로그, 브라우저 히스토리에 토큰이 영원히 남음. (절대 권장하지 않음).

### C. Cookie-based Authentication
전통적인 세션/쿠키 방식입니다.
*   **방식**: 브라우저가 연결 요청 시 자동으로 쿠키(Session ID) 전송.
*   **장점**: 구현 간편 (기존 웹 세션 공유).
*   **단점**: CSRF 공격 방어 필요, 모바일 앱(Native)에서 쿠키 관리 번거로움, Cross-Domain 제약.

### D. STOMP Header Authentication (현재 방식)
STOMP 프로토콜 레벨에서 헤더를 추가하는 방식입니다.
*   **방식**: WebSocket 연결(Handshake) 후, STOMP `CONNECT` 프레임 내부에 헤더 추가.
*   **장점**: URL에 정보 남지 않음.
*   **단점**: WebSocket Handshake 단계에서는 인증 불가 (연결은 일단 맺어짐 -> 이후 `CONNECT`에서 끊음).

### E. Per-Message Authentication (High Security)
모든 메시지 전송 시 인증을 수행하는 방식입니다.
*   **방식**: 클라이언트가 `SEND`, `SUBSCRIBE` 등 모든 프레임마다 `Authorization` 헤더를 포함하고, 서버는 모든 요청마다 토큰을 검증.
*   **장점**: **즉각적인 차단 가능**. 토큰이 만료되거나 블랙리스트에 등록되면 즉시 메시지 전송 차단. 실시간성이 매우 중요한 금융/보안 서비스에서 사용.
*   **단점**: **성능 오버헤드 높음**. 매 메시지마다 JWT 복호화/검증 비용 발생.
