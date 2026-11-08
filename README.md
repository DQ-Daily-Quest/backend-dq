# Daily Quest — Backend

Daily Quest 앱의 백엔드 서버입니다. (Team JJJ · 담당: 임정원)

- 언어/프레임워크: Kotlin 2.3, Spring Boot 4.1 (Java 17)
- DB: PostgreSQL 16
- API 명세: [docs/api.md](docs/api.md) · 실행 후 Swagger UI: http://localhost:8080/swagger-ui.html

## 실행 방법

```bash
# 1. 로컬 PostgreSQL (Docker, 포트 5433)
docker compose up -d

# 2. 서버 실행 (기본 프로필 local, ddl-auto=update)
./gradlew bootRun

# 3. 확인
curl http://localhost:8080/api/v1/health
```

테스트: `./gradlew test` (H2 인메모리 DB 사용, Postgres 불필요)

DB 접속 정보는 `application.yml`의 환경변수로 바꿀 수 있습니다: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `UPLOAD_DIR`.

## 패키지 구조

```
com.dq.backend
├── global            # 도메인과 무관한 공통 코드
│   ├── common        # PageResponse 등 공통 응답
│   ├── config        # Security, OpenAPI, JPA 설정
│   ├── entity        # BaseTimeEntity (createdAt/updatedAt)
│   ├── error         # ErrorCode, BusinessException, GlobalExceptionHandler
│   └── health        # 헬스체크
└── domain            # 기능별 패키지 (api.md 장 순서)
    ├── auth          # 2장 인증
    ├── user          # 3장 사용자
    ├── room          # 4장 룸
    ├── mission       # 5장 미션
    ├── proof         # 6장 인증 제출
    ├── reward        # 7장 보상·상점
    ├── decor         # 8장 데코
    └── notification  # 9장 알림
```

각 도메인 패키지는 `controller / service / repository / entity / dto` 로 나눕니다.

## 공통 규칙 (api.md 1장)

- 성공 응답은 데이터를 그대로 반환, 목록은 `PageResponse`
- 에러 응답은 `{ code, message, timestamp }` — `BusinessException(ErrorCode)`를 던지면 자동 변환
- 인증이 필요한 경로는 `Authorization: Bearer {accessToken}` (JWT 필터는 `feat/auth`에서 추가)

## 브랜치 전략

- `main`: 항상 실행 가능한 상태 유지
- 컴포넌트 단위로 브랜치를 따서 작업 후 PR로 `main`에 병합
  - 예: `docs/api-spec`, `chore/project-setup`, `feat/auth`, `feat/room`, `feat/mission`, `feat/proof`, `feat/reward`, `feat/decor`
- 커밋 메시지: `type: 내용` (`feat`, `fix`, `docs`, `chore`, `refactor`, `test`)
