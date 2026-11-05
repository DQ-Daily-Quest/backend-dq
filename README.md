# Daily Quest — Backend

Daily Quest 앱의 백엔드 서버입니다. (Team JJJ · 담당: 임정원)

- 언어/프레임워크: Kotlin, Spring Boot
- DB: PostgreSQL
- API 명세: [docs/api.md](docs/api.md)

## 브랜치 전략

- `main`: 항상 실행 가능한 상태 유지
- 컴포넌트 단위로 브랜치를 따서 작업 후 PR로 `main`에 병합
  - 예: `docs/api-spec`, `feat/auth`, `feat/room`, `feat/mission`, `feat/proof`, `feat/reward`, `feat/decor`
- 커밋 메시지: `type: 내용` (`feat`, `fix`, `docs`, `chore`, `refactor`, `test`)
