# Chat POC

Spring Boot와 WebSocket을 활용한 1:1 실시간 상담 채팅 POC 애플리케이션입니다.

## 🛠 기술 스택

- **Core**: Kotlin, Spring Boot 3.2
- **Database**: MySQL 8.0, Redis 7 (Pub/Sub)
- **WebSocket**: STOMP, SockJS
- **Frontend**: HTML5, Vanilla JS

## 🚀 시작하기 (Getting Started)

### 1. 인프라 실행 (Docker)
MySQL과 Redis 컨테이너를 실행합니다.
```bash
docker-compose up -d
```

### 2. 애플리케이션 실행
서버를 시작합니다.
```bash
./gradlew bootRun
```
(또는 IntelliJ에서 `ChatPocApplication.kt` 실행)

### 3. 상태 확인
```bash
curl http://localhost:8080/health
```

---

## 🧪 테스트 가이드 (How to Test)

브라우저를 통해 직접 채팅 기능을 테스트해볼 수 있습니다.

### 🌐 접속 주소
**http://localhost:8080/index.html**

### 📝 테스트 시나리오

#### 준비 사항
- 서로 다른 세션을 유지하기 위해 **두 개의 브라우저** (예: Chrome 일반 탭 & 시크릿 탭)를 준비하세요.

#### Step 1: 사용자(User)로 로그인
1. **브라우저 A**에서 `index.html` 접속
2. **[사용자]** 탭 선택
3. 이메일 입력: `user1@email.com` (없는 이메일이면 자동 가입됨)
4. 로그인 후 채팅방 자동 입장
5. 메시지 전송: "안녕하세요, 문의드립니다."

#### Step 2: 관리자(Admin)로 로그인
1. **브라우저 B** (시크릿 탭)에서 `index.html` 접속
2. **[운영자]** 탭 선택
3. 이메일 입력: `admin1@email.com`
4. 로그인 후 **운영자 대시보드** 진입
5. 왼쪽 **채팅 목록**에서 `user1@email.com`의 채팅방에 **배지(Badge)**가 표시된 것 확인

#### Step 3: 실시간 대화 및 읽음 처리
1. Admin이 채팅방 클릭하여 입장
2. User 화면에서 Admin이 메시지를 읽었는지 (읽음 표시 변경) 확인
3. Admin이 답장 전송: "네, 안녕하세요. 무엇을 도와드릴까요?"
4. User/Admin 양쪽 화면에서 실시간 대화 진행

#### Step 4: 상담사 배정 (New ✨)
1. **Admin A**가 `admin1@email.com`으로 로그인
2. **Admin B**가 `admin2@email.com`으로 다른 브라우저에서 로그인
3. User가 메시지를 보내면, 두 Admin의 **[미배정]** 탭에 채팅방 등장
4. **Admin A**가 해당 채팅방 클릭 후 상단의 **'상담 시작하기'** 버튼 클릭
5. **결과**:
   - **Admin A**: 해당 방이 **[내 상담]** 탭으로 이동하고 대화 입력이 활성화됨
   - **Admin B**: 해당 방이 **[미배정]** 목록에서 실시간으로 사라짐 (WebSocket 알림)

## 📂 프로젝트 구조

```
src/main/kotlin/com/chat/poc/
├── ChatPocApplication.kt     # 메인 애플리케이션
├── domain/                   # 도메인 계층 (Entity, Repository)
├── application/              # 응용 계층 (Service, Business Logic)
├── presentation/             # 표현 계층 (Controller, DTO)
└── infrastructure/           # 인프라 계층 (Config, Redis Publisher/Subscriber)
```

## 📚 문서 (Documents)
- [워크플로우 가이드 (Workflow Guide)](WORKFLOW_GUIDE.md): 백엔드 로직 및 메시지 스펙 상세 설명
