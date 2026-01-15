# StompSubProtocolHandler: Roles & Responsibilities

`StompSubProtocolHandler`는 Spring WebSocket 아키텍처에서 **WebSocket 레벨(Raw Level)과 Spring Message 레벨(Application Level)을 잇는 핵심 브리지(Bridge)**이자, **STOMP 프로토콜 규약을 강제하는 관리자**입니다.

이 컴포넌트의 7가지 핵심 역할을 정리합니다.

---

## 1. 프레임 파서 & 인코더 (Codec Role)
*   **Decoding (Inbound)**: WebSocket을 통해 들어오는 `TEXT` 또는 `BINARY` 바이트 스트림을 읽어, STOMP 규약에 따라 `Command`, `Header`, `Body`로 파싱하여 Spring의 `Message<?>` 객체로 변환합니다.
*   **Encoding (Outbound)**: 서버에서 나가는 `Message` 객체를 다시 STOMP 프레임 문자열(예: `CONNECTED\nversion:1.2\n\n\0`)로 직렬화하여 클라이언트에게 전송합니다.

## 2. 세션 라이프사이클 & 상태 관리 (State Machine Role)
*   **Session Mapping**: 각 WebSocket 세션에 대해 STOMP 세션 상태를 관리합니다.
*   **Connection Lifecycle**:
    *   첫 프레임이 반드시 `CONNECT` 또는 `STOMP` 커맨드인지 확인합니다.
    *   올바른 연결 시 `CONNECTED` 프레임을 발송하고 상태를 `CONNECTED`로 변경합니다.
    *   `DISCONNECT` 커맨드 수신 시 세션을 정리하고 WebSocket 연결 종료(`close`)를 유도합니다.

## 3. 프로토콜 검증 및 강제 (Enforcer Role)
*   **Sequence Check**: `CONNECT` 없이 `SEND`나 `SUBSCRIBE` 등의 명령이 들어오면, 즉시 `ERROR` 프레임을 보내고 연결을 끊습니다. (이로 인해 커스텀 인터셉터 없이도 1차적인 보안이 보장됩니다.)
*   **Validation**: 잘못된 커맨드, 필수 헤더 누락(`destination` 등), 프레임 포맷 오류(NULL byte 누락 등)를 감지하여 처리합니다.

## 4. Heartbeat 협상 & 감시 (Keep-alive Role)
*   **Negotiation**: `CONNECT` 프레임의 `heart-beat` 헤더 값을 파싱하여 클라이언트와 서버 간의 송수신 주기를 협상합니다.
*   **Monitoring**: 협상된 주기에 따라 주기적으로 `Ping/Pong`(접속 유지 신호)을 교환합니다.
*   **Termination**: 정해진 시간 내에 신호가 오지 않을 경우, 좀비 커넥션으로 간주하고 세션을 강제 종료합니다. (단, 서버 측 `TaskScheduler` 설정 필요)

## 5. ACK/NACK 및 응답 처리 (Response Role)
*   **Receipt**: 클라이언트가 중요한 작업 후 확인을 받기 위해 `receipt` 헤더를 보낸 경우, 작업 완료 시 `RECEIPT` 프레임을 생성하여 응답합니다.
*   **Ack/Nack**: 메시지 수신 확인(`ACK`)이나 거부(`NACK`) 처리를 지원하여, 메시지 전달 보장성(Reliability)을 높입니다.

## 6. 구독 및 메시지 라우팅 (Router Role)
*   **Converting**: STOMP 명령(`SUBSCRIBE`, `UnSUBSCRIBE`, `SEND`)을 적절한 Spring Message로 변환하여 내부 파이프라인(`clientInboundChannel`)으로 흘려보냅니다.
*   **Subscription**: `SimpSubscriptionRegistry`와 연동하여 구독 정보를 등록하고 해제하는 입구 역할을 수행합니다.

## 7. 오류 처리의 최전선 (Error Gateway Role)
*   **Protocol Errors**: 위에서 언급한 프로토콜 위반 사항 발생 시 직접 `ERROR` 프레임을 생성합니다.
*   **Exception Handling**: Spring 내부 파이프라인(Interceptor, Controller 등)에서 던져진 예외가 `StompSubProtocolErrorHandler`를 통해 처리되어 내려오면, 이를 최종적으로 STOMP `ERROR` 프레임으로 변환하여 클라이언트에게 전송하고 TCP 연결을 종료합니다.

---

> **요약**: 개발자가 Controller에서 비즈니스 로직에만 집중할 수 있는 이유는, 이 핸들러가 **"STOMP라는 언어의 문법 검사, 번역, 신호등 관리, 에러 처리"**를 모두 전담하고 있기 때문입니다.
