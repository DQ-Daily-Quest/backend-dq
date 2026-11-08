# Daily Quest API 명세서

> 버전: v0.1 (초안) · 작성일: 2026-11-05 · 담당: 백엔드(임정원)
>
> 코드가 올라간 뒤에는 Swagger UI(`/swagger-ui.html`)가 기준이 되고, 이 문서는 설계 의도와 흐름 설명 위주로 유지합니다.

## 목차

1. [공통 규칙](#1-공통-규칙)
2. [인증 (Auth)](#2-인증-auth)
3. [사용자 (User)](#3-사용자-user)
4. [룸 (Room)](#4-룸-room)
5. [미션 (Mission)](#5-미션-mission)
6. [인증 제출 (Proof)](#6-인증-제출-proof)
7. [보상 · 상점 (Reward / Shop)](#7-보상--상점-reward--shop)
8. [데코 (Decor)](#8-데코-decor)
9. [알림 (Notification)](#9-알림-notification)
10. [도메인 모델 요약](#10-도메인-모델-요약)
11. [MVP 우선순위](#11-mvp-우선순위)

---

## 1. 공통 규칙

### 1.1 기본 정보

| 항목 | 값 |
|---|---|
| Base URL (로컬) | `http://localhost:8080/api/v1` |
| 프로토콜 | HTTPS (배포), HTTP (로컬) |
| 데이터 형식 | `application/json; charset=utf-8` (파일 업로드만 `multipart/form-data`) |
| 날짜/시간 | ISO 8601, UTC. 예: `2026-11-05T09:30:00Z` |
| 날짜(일 단위) | `YYYY-MM-DD`, 서버 기준 KST(Asia/Seoul)로 "오늘" 판정 |
| ID | 숫자 `Long` |

### 1.2 인증 방식

- 로그인 성공 시 **JWT Access Token**(유효 1시간)과 **Refresh Token**(유효 14일)을 발급합니다.
- 인증이 필요한 요청은 헤더에 토큰을 넣습니다.

```
Authorization: Bearer {accessToken}
```

- 아래 표에서 🔒 표시가 있는 API는 인증 필수입니다.

### 1.3 응답 형식

성공 응답은 데이터를 **그대로** 반환합니다. (별도 래퍼 없음)

```json
{ "id": 1, "name": "JJJ Room" }
```

목록 응답은 페이지 정보를 포함합니다.

```json
{
  "content": [ { "id": 1, "name": "JJJ Room" } ],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3
}
```

에러 응답은 항상 아래 형식입니다.

```json
{
  "code": "ROOM_NOT_FOUND",
  "message": "존재하지 않는 룸입니다.",
  "timestamp": "2026-11-05T09:30:00Z"
}
```

### 1.4 HTTP 상태 코드

| 코드 | 의미 |
|---|---|
| 200 | 성공 |
| 201 | 생성 성공 |
| 204 | 성공, 응답 본문 없음 (삭제 등) |
| 400 | 요청 값 오류 (유효성 검증 실패) |
| 401 | 토큰 없음 / 만료 / 위조 |
| 403 | 권한 없음 (예: 룸 멤버가 아님) |
| 404 | 리소스 없음 |
| 409 | 충돌 (중복 이메일, 이미 가입된 룸, 중복 제출 등) |
| 500 | 서버 오류 |

### 1.5 공통 에러 코드

| code | HTTP | 설명 |
|---|---|---|
| `VALIDATION_ERROR` | 400 | 필수 값 누락, 형식 오류 |
| `UNAUTHORIZED` | 401 | 인증 실패 |
| `TOKEN_EXPIRED` | 401 | Access Token 만료 → refresh 필요 |
| `FORBIDDEN` | 403 | 권한 없음 |
| `NOT_FOUND` | 404 | 리소스 없음 (세부 코드는 각 섹션 참고) |
| `INTERNAL_ERROR` | 500 | 서버 오류 |

### 1.6 페이지네이션 쿼리

목록 API 공통 쿼리 파라미터입니다.

| 파라미터 | 기본값 | 설명 |
|---|---|---|
| `page` | 0 | 0부터 시작 |
| `size` | 20 | 최대 100 |
| `sort` | API별 상이 | 예: `createdAt,desc` |

---

## 2. 인증 (Auth)

| 메서드 | 경로 | 설명 | 인증 |
|---|---|---|---|
| POST | `/auth/signup` | 회원가입 | |
| POST | `/auth/login` | 로그인 | |
| POST | `/auth/refresh` | 토큰 재발급 | |
| POST | `/auth/logout` | 로그아웃 (Refresh Token 폐기) | 🔒 |

### 2.1 회원가입 `POST /auth/signup`

요청

```json
{
  "email": "jeongwon@example.com",
  "password": "P@ssw0rd!",
  "nickname": "정원"
}
```

| 필드 | 타입 | 필수 | 제약 |
|---|---|---|---|
| email | string | O | 이메일 형식, 유일 |
| password | string | O | 8~64자 |
| nickname | string | O | 2~12자, 유일 |

응답 `201`

```json
{
  "userId": 1,
  "email": "jeongwon@example.com",
  "nickname": "정원"
}
```

에러: `DUPLICATE_EMAIL` (409), `DUPLICATE_NICKNAME` (409), `VALIDATION_ERROR` (400)

### 2.2 로그인 `POST /auth/login`

요청

```json
{ "email": "jeongwon@example.com", "password": "P@ssw0rd!" }
```

응답 `200`

```json
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "eyJhbGciOi...",
  "expiresIn": 3600,
  "user": { "userId": 1, "nickname": "정원", "avatarUrl": null, "coins": 0 }
}
```

에러: `INVALID_CREDENTIALS` (401)

### 2.3 토큰 재발급 `POST /auth/refresh`

요청

```json
{ "refreshToken": "eyJhbGciOi..." }
```

응답 `200`: 2.2와 동일한 토큰 구조 (Refresh Token도 새로 발급, 기존 것은 폐기)

에러: `INVALID_REFRESH_TOKEN` (401)

### 2.4 로그아웃 `POST /auth/logout` 🔒

요청 본문 없음. 서버에 저장된 Refresh Token을 폐기합니다.

응답 `204`

---

## 3. 사용자 (User)

| 메서드 | 경로 | 설명 | 인증 |
|---|---|---|---|
| GET | `/users/me` | 내 정보 | 🔒 |
| PATCH | `/users/me` | 내 정보 수정 (닉네임, 아바타 구성) | 🔒 |
| GET | `/users/{userId}` | 다른 사용자 공개 프로필 | 🔒 |
| PUT | `/users/me/fcm-token` | 푸시 알림 토큰 등록 | 🔒 |

### 3.1 내 정보 `GET /users/me` 🔒

응답 `200`

```json
{
  "userId": 1,
  "email": "jeongwon@example.com",
  "nickname": "정원",
  "coins": 350,
  "avatar": {
    "hair": 12,
    "top": 5,
    "bottom": 3,
    "accessory": null
  },
  "stats": {
    "roomCount": 2,
    "completedMissionCount": 17
  },
  "createdAt": "2026-11-05T09:30:00Z"
}
```

`avatar`의 각 값은 보유한 아이템(`items.id`)의 ID입니다. 착용 안 함은 `null`.

### 3.2 내 정보 수정 `PATCH /users/me` 🔒

보내는 필드만 수정됩니다.

```json
{
  "nickname": "정원이",
  "avatar": { "hair": 12, "top": 7, "bottom": 3, "accessory": 21 }
}
```

에러: `DUPLICATE_NICKNAME` (409), `ITEM_NOT_OWNED` (403, 보유하지 않은 아이템 착용 시)

### 3.3 공개 프로필 `GET /users/{userId}` 🔒

응답 `200`: `userId`, `nickname`, `avatar`, `stats` 만 반환 (이메일, 코인 제외)

### 3.4 FCM 토큰 등록 `PUT /users/me/fcm-token` 🔒

```json
{ "token": "fcm-device-token..." }
```

응답 `204`

---

## 4. 룸 (Room)

| 메서드 | 경로 | 설명 | 인증 |
|---|---|---|---|
| GET | `/rooms` | 공개 룸 목록 (홈 Public 탭) | 🔒 |
| GET | `/rooms/mine` | 내가 속한 룸 목록 (홈 Private 탭) | 🔒 |
| POST | `/rooms` | 룸 생성 | 🔒 |
| GET | `/rooms/{roomId}` | 룸 상세 | 🔒 |
| PATCH | `/rooms/{roomId}` | 룸 수정 (방장) | 🔒 |
| DELETE | `/rooms/{roomId}` | 룸 삭제 (방장) | 🔒 |
| POST | `/rooms/{roomId}/join` | 공개 룸 참여 | 🔒 |
| POST | `/rooms/join` | 초대코드로 비공개 룸 참여 | 🔒 |
| DELETE | `/rooms/{roomId}/leave` | 룸 나가기 | 🔒 |
| GET | `/rooms/{roomId}/members` | 멤버 목록 (아바타 포함) | 🔒 |
| POST | `/rooms/{roomId}/invite-code` | 초대코드 재발급 (방장) | 🔒 |

### 4.1 공개 룸 목록 `GET /rooms` 🔒

쿼리: 페이지네이션 + `keyword`(이름 검색, 선택), `sort` 기본 `popularity,desc` (그 외 `memberCount,desc`, `createdAt,desc` 허용)

응답 `200`

```json
{
  "content": [
    {
      "roomId": 3,
      "name": "Study Crew",
      "description": "매일 공부 인증",
      "isPublic": true,
      "memberCount": 6,
      "maxMembers": 10,
      "thumbnailUrl": null,
      "isHot": true,
      "joined": false
    }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

`joined`: 요청자가 이미 멤버인지.

**인기 점수 정렬 규칙 (서버 계산)**

- 인기 점수 = (최근 7일 인증 제출 수 × 2) + 멤버 수. 목록 조회 시 서버가 계산해 정렬합니다.
- 기본 정렬은 인기 점수 내림차순, 동점이면 생성일 최신순입니다.
- `isHot`: 인기 점수 상위 룸에 표시합니다. 기준(상위 3개 / 상위 10% / 10점 이상)은 미결정이며 기본안은 상위 3개입니다.
- 방 카테고리나 사용자 조회 기록 기반 추천은 MVP 범위 밖이며 후순위입니다.

### 4.2 내 룸 목록 `GET /rooms/mine` 🔒

응답 `200`: 4.1과 동일한 항목 배열 (페이지네이션 없음). 각 항목에 다음이 추가됩니다.

```json
{
  "todayMissionDone": false,
  "pendingProofCount": 2
}
```

### 4.3 룸 생성 `POST /rooms` 🔒

```json
{
  "name": "JJJ Room",
  "description": "우리 셋의 데일리 퀘스트",
  "isPublic": false,
  "maxMembers": 10
}
```

| 필드 | 타입 | 필수 | 제약 |
|---|---|---|---|
| name | string | O | 2~20자 |
| description | string | | 최대 100자 |
| isPublic | boolean | O | |
| maxMembers | int | | 2~30, 기본 10 |

응답 `201`

```json
{
  "roomId": 1,
  "name": "JJJ Room",
  "isPublic": false,
  "inviteCode": "A7K2Q9",
  "ownerId": 1
}
```

생성자는 자동으로 `OWNER` 역할의 멤버가 됩니다. `inviteCode`는 비공개 룸일 때만 발급됩니다.

### 4.4 룸 상세 `GET /rooms/{roomId}` 🔒

비공개 룸은 멤버만 조회 가능합니다.

```json
{
  "roomId": 1,
  "name": "JJJ Room",
  "description": "우리 셋의 데일리 퀘스트",
  "isPublic": false,
  "inviteCode": "A7K2Q9",
  "owner": { "userId": 1, "nickname": "정원" },
  "memberCount": 3,
  "maxMembers": 10,
  "myRole": "OWNER",
  "todayMission": { "missionId": 10, "content": "오늘의 하늘 사진 찍기 ☁️" },
  "createdAt": "2026-11-05T09:30:00Z"
}
```

`inviteCode`는 멤버에게만 노출됩니다. `myRole`: `OWNER` | `MEMBER` | `null`(비멤버)

에러: `ROOM_NOT_FOUND` (404), `FORBIDDEN` (403)

### 4.5 룸 수정 `PATCH /rooms/{roomId}` 🔒 (방장)

`name`, `description`, `isPublic`, `maxMembers` 중 보내는 것만 수정. 에러: `FORBIDDEN` (403)

### 4.6 룸 삭제 `DELETE /rooms/{roomId}` 🔒 (방장)

응답 `204`. 멤버, 미션, 인증, 데코가 함께 삭제됩니다.

### 4.7 공개 룸 참여 `POST /rooms/{roomId}/join` 🔒

응답 `200`: 4.4 룸 상세

에러: `ROOM_NOT_PUBLIC` (403), `ALREADY_JOINED` (409), `ROOM_FULL` (409)

### 4.8 초대코드 참여 `POST /rooms/join` 🔒

```json
{ "inviteCode": "A7K2Q9" }
```

응답 `200`: 4.4 룸 상세

에러: `INVALID_INVITE_CODE` (404), `ALREADY_JOINED` (409), `ROOM_FULL` (409)

### 4.9 룸 나가기 `DELETE /rooms/{roomId}/leave` 🔒

응답 `204`. 방장은 다른 멤버가 남아 있으면 나갈 수 없습니다 (`OWNER_CANNOT_LEAVE`, 409). 마지막 멤버가 나가면 룸이 삭제됩니다.

### 4.10 멤버 목록 `GET /rooms/{roomId}/members` 🔒

룸 내부 화면에서 멤버 아바타를 그릴 때 사용합니다.

```json
[
  {
    "userId": 1,
    "nickname": "정원",
    "role": "OWNER",
    "avatar": { "hair": 12, "top": 5, "bottom": 3, "accessory": null },
    "todayMissionDone": true,
    "joinedAt": "2026-11-05T09:30:00Z"
  }
]
```

### 4.11 초대코드 재발급 `POST /rooms/{roomId}/invite-code` 🔒 (방장)

응답 `200`: `{ "inviteCode": "Z3P8M1" }`

---

## 5. 미션 (Mission)

미션은 두 종류입니다.

| type | 생성 주체 | 보상 | 설명 |
|---|---|---|---|
| `APP` | 서버 (매일 자동) | 아바타/가구 아이템 | 룸마다 하루 1개, 미션 풀(`mission_templates`)에서 랜덤 선정 |
| `MEMBER` | 룸 멤버 | 코인 | 멤버가 직접 만드는 미션, 마감일 지정 가능 |

**APP 미션 생성 규칙 (서버 핵심 로직)**

- 매일 KST 00:00에 모든 룸에 대해 그날의 APP 미션을 1개 생성합니다.
- 같은 룸에서 최근 14일 내에 나온 템플릿은 제외하고 랜덤 선정합니다.
- 스케줄러가 돌기 전에 조회 요청이 오면 그 시점에 생성합니다 (lazy 생성). 두 경로가 동시에 들어와도 `(room_id, date)` 유니크 제약으로 하루 1개만 보장합니다.

| 메서드 | 경로 | 설명 | 인증 |
|---|---|---|---|
| GET | `/rooms/{roomId}/missions` | 룸 미션 목록 | 🔒 멤버 |
| GET | `/rooms/{roomId}/missions/today` | 오늘의 APP 미션 | 🔒 멤버 |
| POST | `/rooms/{roomId}/missions` | 멤버 미션 생성 | 🔒 멤버 |
| GET | `/missions/{missionId}` | 미션 상세 | 🔒 멤버 |
| PATCH | `/missions/{missionId}` | 멤버 미션 수정 (작성자) | 🔒 |
| DELETE | `/missions/{missionId}` | 멤버 미션 삭제 (작성자/방장) | 🔒 |
| GET | `/missions/mine` | 내 룸 전체의 진행 중 미션 (하단 탭 Missions) | 🔒 |

### 5.1 룸 미션 목록 `GET /rooms/{roomId}/missions` 🔒

쿼리: `status` = `ACTIVE`(기본) | `EXPIRED` | `ALL`, `type` = `APP` | `MEMBER` | 생략(전체), 페이지네이션

```json
{
  "content": [
    {
      "missionId": 10,
      "type": "APP",
      "content": "오늘의 하늘 사진 찍기 ☁️",
      "reward": { "type": "ITEM", "item": { "itemId": 21, "name": "구름 모빌", "imageUrl": "/assets/items/21.png" } },
      "createdBy": null,
      "date": "2026-11-05",
      "dueAt": "2026-11-05T14:59:59Z",
      "myProof": { "proofId": 55, "status": "APPROVED" },
      "proofCount": 2,
      "createdAt": "2026-11-04T15:00:00Z"
    },
    {
      "missionId": 11,
      "type": "MEMBER",
      "content": "10분 이상 산책하기",
      "reward": { "type": "COIN", "amount": 100 },
      "createdBy": { "userId": 2, "nickname": "진영" },
      "date": null,
      "dueAt": "2026-11-07T14:59:59Z",
      "myProof": null,
      "proofCount": 0,
      "createdAt": "2026-11-05T01:20:00Z"
    }
  ],
  "page": 0, "size": 20, "totalElements": 2, "totalPages": 1
}
```

`myProof`: 요청자가 제출한 인증이 있으면 상태 요약, 없으면 `null`.

### 5.2 오늘의 APP 미션 `GET /rooms/{roomId}/missions/today` 🔒

응답 `200`: 5.1의 단일 미션 객체 (없으면 이 시점에 생성해서 반환)

### 5.3 멤버 미션 생성 `POST /rooms/{roomId}/missions` 🔒

```json
{
  "content": "10분 이상 산책하기",
  "rewardCoins": 100,
  "dueAt": "2026-11-07T14:59:59Z"
}
```

| 필드 | 타입 | 필수 | 제약 |
|---|---|---|---|
| content | string | O | 2~100자 |
| rewardCoins | int | O | 10~500, 10단위 |
| dueAt | datetime | | 미래 시각, 기본 생성 후 7일 |

응답 `201`: 5.1의 단일 미션 객체

에러: `FORBIDDEN` (403, 멤버 아님), `VALIDATION_ERROR` (400)

### 5.4 미션 상세 `GET /missions/{missionId}` 🔒

5.1의 단일 미션 객체 + 아래 필드

```json
{
  "proofs": [
    { "proofId": 55, "user": { "userId": 1, "nickname": "정원" }, "status": "APPROVED", "thumbnailUrl": "...", "createdAt": "..." }
  ]
}
```

### 5.5 ~ 5.6 수정 / 삭제

- `PATCH /missions/{missionId}`: `content`, `dueAt` 수정. 이미 인증이 1개 이상 제출됐으면 수정 불가 (`MISSION_HAS_PROOFS`, 409). APP 미션은 수정 불가.
- `DELETE /missions/{missionId}`: 작성자 또는 방장. 응답 `204`.

### 5.7 내 미션 모아보기 `GET /missions/mine` 🔒

하단 탭 Missions 화면용. 내가 속한 모든 룸의 `ACTIVE` 미션을 룸 정보와 함께 반환합니다.

```json
[
  {
    "room": { "roomId": 1, "name": "JJJ Room" },
    "missions": [ /* 5.1 미션 객체 */ ]
  }
]
```

---

## 6. 인증 제출 (Proof)

미션 수행 증거(사진/영상/텍스트)를 올리고 멤버 승인을 받는 흐름입니다.

**승인 규칙 (서버 핵심 로직)**

- 제출자 본인을 제외한 룸 멤버가 승인/거절 투표를 합니다.
- **승인 수가 `ceil((멤버 수 - 1) / 2)` 이상**이면 `APPROVED`로 확정하고 즉시 보상을 지급합니다. 멤버가 혼자인 룸은 제출 즉시 자동 승인됩니다.
- 거절 수가 같은 기준에 도달하면 `REJECTED`로 확정합니다. 거절된 미션은 다시 제출할 수 있습니다.
- 확정 전까지는 `PENDING`이며, 미션 마감 후 24시간이 지나도 미확정이면 `EXPIRED`로 처리합니다.
- 보상 지급은 인증 확정과 **같은 트랜잭션**에서 처리하며, 한 인증당 1회만 지급됩니다.

| 메서드 | 경로 | 설명 | 인증 |
|---|---|---|---|
| POST | `/uploads` | 이미지/영상 업로드 (URL 반환) | 🔒 |
| POST | `/missions/{missionId}/proofs` | 인증 제출 | 🔒 멤버 |
| GET | `/proofs/{proofId}` | 인증 상세 (스토리 보기) | 🔒 멤버 |
| DELETE | `/proofs/{proofId}` | 인증 삭제 (PENDING 상태, 본인) | 🔒 |
| POST | `/proofs/{proofId}/approve` | 승인 | 🔒 멤버 |
| POST | `/proofs/{proofId}/reject` | 거절 | 🔒 멤버 |
| POST | `/proofs/{proofId}/reactions` | 리액션 (이모지) | 🔒 멤버 |
| DELETE | `/proofs/{proofId}/reactions` | 리액션 취소 | 🔒 멤버 |
| GET | `/rooms/{roomId}/feed` | 룸 피드 (스토리 링용, 최근 인증) | 🔒 멤버 |
| GET | `/rooms/{roomId}/proofs/pending` | 내가 승인해야 할 인증 목록 | 🔒 멤버 |

### 6.1 파일 업로드 `POST /uploads` 🔒

`multipart/form-data`

| 필드 | 설명 |
|---|---|
| file | 이미지(jpg, png, webp, 최대 10MB) 또는 영상(mp4, 최대 50MB, 30초) |
| purpose | `PROOF` \| `ROOM_THUMBNAIL` |

응답 `201`

```json
{
  "url": "https://storage.example.com/proofs/2026/11/05/uuid.jpg",
  "thumbnailUrl": "https://storage.example.com/proofs/2026/11/05/uuid_thumb.jpg",
  "mediaType": "IMAGE",
  "width": 1080,
  "height": 1920
}
```

에러: `FILE_TOO_LARGE` (400), `UNSUPPORTED_MEDIA_TYPE` (400)

> 구현 메모: 초기에는 서버 로컬 디스크에 저장하고 `/files/**`로 서빙합니다. 배포 시 Firebase Storage 또는 S3로 교체할 수 있도록 `StorageService` 인터페이스로 분리합니다.

### 6.2 인증 제출 `POST /missions/{missionId}/proofs` 🔒

```json
{
  "mediaUrl": "https://storage.example.com/proofs/.../uuid.jpg",
  "thumbnailUrl": "https://storage.example.com/proofs/.../uuid_thumb.jpg",
  "mediaType": "IMAGE",
  "text": "오늘 노을 진짜 예뻤음"
}
```

| 필드 | 타입 | 필수 | 제약 |
|---|---|---|---|
| mediaUrl | string | △ | `mediaUrl` 또는 `text` 중 하나는 필수 |
| thumbnailUrl | string | | |
| mediaType | `IMAGE` \| `VIDEO` \| `TEXT` | O | |
| text | string | △ | 최대 300자 |

응답 `201`

```json
{
  "proofId": 55,
  "missionId": 10,
  "user": { "userId": 1, "nickname": "정원", "avatar": { "...": "..." } },
  "mediaUrl": "...",
  "thumbnailUrl": "...",
  "mediaType": "IMAGE",
  "text": "오늘 노을 진짜 예뻤음",
  "status": "PENDING",
  "approval": { "approved": 0, "rejected": 0, "required": 1, "myVote": null },
  "reactions": [],
  "createdAt": "2026-11-05T10:00:00Z"
}
```

에러: `MISSION_EXPIRED` (409), `PROOF_ALREADY_SUBMITTED` (409, 같은 미션에 PENDING/APPROVED 인증이 이미 있음), `FORBIDDEN` (403)

### 6.3 인증 상세 `GET /proofs/{proofId}` 🔒

응답 `200`: 6.2 응답 + `mission` 요약 + `room` 요약

```json
{
  "mission": { "missionId": 10, "type": "APP", "content": "오늘의 하늘 사진 찍기 ☁️" },
  "room": { "roomId": 1, "name": "JJJ Room" },
  "approval": {
    "approved": 2, "rejected": 0, "required": 1, "myVote": "APPROVE",
    "voters": [ { "userId": 2, "nickname": "진영", "vote": "APPROVE" } ]
  },
  "reactions": [
    { "emoji": "❤️", "count": 2, "reacted": true },
    { "emoji": "🔥", "count": 1, "reacted": false }
  ]
}
```

### 6.4 승인 / 거절 `POST /proofs/{proofId}/approve` · `/reject` 🔒

요청 본문 없음. 본인 인증에는 투표할 수 없습니다. 이미 투표한 경우 투표를 변경합니다.

응답 `200`

```json
{
  "proofId": 55,
  "status": "APPROVED",
  "approval": { "approved": 2, "rejected": 0, "required": 1, "myVote": "APPROVE" },
  "rewardGranted": { "type": "ITEM", "item": { "itemId": 21, "name": "구름 모빌" } }
}
```

`rewardGranted`: 이 투표로 인증이 확정되어 보상이 지급된 경우에만 포함.

에러: `CANNOT_VOTE_OWN_PROOF` (403), `PROOF_ALREADY_FINALIZED` (409)

### 6.5 리액션 `POST /proofs/{proofId}/reactions` 🔒

```json
{ "emoji": "❤️" }
```

허용 이모지: `❤️ 🔥 👏 😂 😮` (서버 화이트리스트). 한 사용자가 한 인증에 같은 이모지는 1번만. 응답 `200`: 리액션 목록. 취소는 `DELETE` + 같은 본문.

### 6.6 룸 피드 `GET /rooms/{roomId}/feed` 🔒

홈 화면 **스토리 링**과 룸 내부 **Feed 탭**에 사용합니다. 최근 24시간 인증이 기본, 쿼리 `since`(datetime)로 조정 가능. 페이지네이션, 최신순.

```json
{
  "content": [
    {
      "proofId": 55,
      "user": { "userId": 1, "nickname": "정원", "avatar": { "...": "..." } },
      "mission": { "missionId": 10, "content": "오늘의 하늘 사진 찍기 ☁️" },
      "mediaType": "IMAGE",
      "thumbnailUrl": "...",
      "status": "APPROVED",
      "viewed": false,
      "createdAt": "2026-11-05T10:00:00Z"
    }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

`viewed`: 요청자가 6.3 상세를 조회한 적이 있는지 (스토리 링 읽음 표시용).

### 6.7 승인 대기 목록 `GET /rooms/{roomId}/proofs/pending` 🔒

내가 아직 투표하지 않은 `PENDING` 인증 목록. 응답 형식은 6.6과 동일.

---

## 7. 보상 · 상점 (Reward / Shop)

**보상 지급 규칙**

| 미션 type | 보상 | 지급 시점 |
|---|---|---|
| APP | 미션에 연결된 아이템 1개 (이미 보유 시 아이템 가격의 50% 코인으로 대체) | 인증 APPROVED 확정 시 |
| MEMBER | 미션에 설정된 코인 | 인증 APPROVED 확정 시 |

모든 코인 변동은 `coin_transactions`에 기록합니다 (지급, 구매, 대체 지급).

| 메서드 | 경로 | 설명 | 인증 |
|---|---|---|---|
| GET | `/shop/items` | 상점 아이템 목록 | 🔒 |
| GET | `/shop/items/{itemId}` | 아이템 상세 | 🔒 |
| POST | `/shop/items/{itemId}/purchase` | 아이템 구매 | 🔒 |
| GET | `/users/me/items` | 내 보유 아이템 | 🔒 |
| GET | `/users/me/coins/transactions` | 코인 내역 | 🔒 |

### 7.1 상점 목록 `GET /shop/items` 🔒

쿼리: `type` = `AVATAR` | `FURNITURE`, `category` (예: `hair`, `top`, `bottom`, `accessory`, `chair`, `plant`, `wall`), 페이지네이션

```json
{
  "content": [
    {
      "itemId": 21,
      "name": "구름 모빌",
      "type": "FURNITURE",
      "category": "ceiling",
      "price": 200,
      "imageUrl": "/assets/items/21.png",
      "owned": false,
      "obtainableBy": "SHOP"
    }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

`obtainableBy`: `SHOP`(구매 가능) | `MISSION_ONLY`(APP 미션 보상 전용) | `BOTH`

### 7.2 구매 `POST /shop/items/{itemId}/purchase` 🔒

요청 본문 없음. 응답 `200`

```json
{ "itemId": 21, "remainingCoins": 150 }
```

에러: `INSUFFICIENT_COINS` (409), `ITEM_ALREADY_OWNED` (409), `ITEM_NOT_PURCHASABLE` (409)

### 7.3 내 아이템 `GET /users/me/items` 🔒

쿼리: `type`. 응답: 7.1의 아이템 객체 배열 + `acquiredAt`, `acquiredBy`(`SHOP` | `MISSION`)

### 7.4 코인 내역 `GET /users/me/coins/transactions` 🔒

```json
{
  "content": [
    { "transactionId": 9, "amount": 100, "balanceAfter": 350, "reason": "MISSION_REWARD", "refId": 55, "createdAt": "..." },
    { "transactionId": 8, "amount": -200, "balanceAfter": 250, "reason": "PURCHASE", "refId": 21, "createdAt": "..." }
  ],
  "page": 0, "size": 20, "totalElements": 2, "totalPages": 1
}
```

---

## 8. 데코 (Decor)

룸 안에 가구를 배치합니다. 배치 좌표는 클라이언트 캔버스 기준 **0.0~1.0 비율**로 저장해 화면 크기에 독립적으로 만듭니다.

| 메서드 | 경로 | 설명 | 인증 |
|---|---|---|---|
| GET | `/rooms/{roomId}/decorations` | 배치 목록 | 🔒 멤버 |
| PUT | `/rooms/{roomId}/decorations` | 배치 전체 저장 (덮어쓰기) | 🔒 멤버 |
| GET | `/rooms/{roomId}/decorations/available` | 멤버들이 보유한 가구 중 배치 가능한 목록 | 🔒 멤버 |

### 8.1 배치 목록 `GET /rooms/{roomId}/decorations` 🔒

```json
{
  "roomId": 1,
  "background": { "itemId": 3, "imageUrl": "/assets/items/3.png" },
  "placements": [
    {
      "placementId": 101,
      "item": { "itemId": 21, "name": "구름 모빌", "imageUrl": "/assets/items/21.png" },
      "ownerId": 1,
      "x": 0.42,
      "y": 0.18,
      "z": 2,
      "flipped": false
    }
  ],
  "updatedAt": "2026-11-05T11:00:00Z",
  "updatedBy": { "userId": 1, "nickname": "정원" }
}
```

### 8.2 배치 저장 `PUT /rooms/{roomId}/decorations` 🔒

편집 화면에서 완료 버튼을 누를 때 전체 배치를 한 번에 보냅니다.

```json
{
  "backgroundItemId": 3,
  "placements": [
    { "itemId": 21, "x": 0.42, "y": 0.18, "z": 2, "flipped": false }
  ]
}
```

규칙: `itemId`는 **룸 멤버 중 누군가가 보유한** 가구여야 합니다 (`ITEM_NOT_OWNED_IN_ROOM`, 403). 같은 아이템을 여러 개 배치하려면 보유 수량만큼만 가능합니다. 응답 `200`: 8.1 형식.

> 동시 편집 충돌은 MVP에서 "마지막 저장 우선"으로 처리하고, 응답의 `updatedBy`로 누가 바꿨는지만 보여줍니다.

---

## 9. 알림 (Notification)

MVP 이후 단계입니다. 서버는 FCM 토큰(3.4)만 먼저 저장하고, 발송은 12월 1주차 이후 여유가 있을 때 붙입니다.

| 이벤트 | 수신자 | 내용 |
|---|---|---|
| 새 APP 미션 생성 | 룸 전체 | "오늘의 미션이 도착했어요" |
| 멤버 미션 생성 | 룸 전체 (작성자 제외) | "{닉네임}님이 새 미션을 만들었어요" |
| 인증 제출 | 룸 전체 (제출자 제외) | "{닉네임}님의 인증을 확인해 주세요" |
| 인증 승인 확정 | 제출자 | "미션 완료! 보상이 지급됐어요" |
| 인증 거절 확정 | 제출자 | "인증이 반려됐어요" |

| 메서드 | 경로 | 설명 | 인증 |
|---|---|---|---|
| GET | `/notifications` | 내 알림 목록 | 🔒 |
| POST | `/notifications/read-all` | 모두 읽음 | 🔒 |

---

## 10. 도메인 모델 요약

```
users ──< room_members >── rooms ──< missions ──< proofs ──< proof_votes
  │                          │          │           └──< proof_reactions
  │                          │          └── mission_templates (APP 미션 풀)
  ├──< user_items >── items ─┤
  ├──< coin_transactions     └──< room_decorations
  └──< refresh_tokens
```

| 테이블 | 주요 컬럼 | 비고 |
|---|---|---|
| users | id, email(uq), password_hash, nickname(uq), coins, avatar_json, fcm_token, created_at | |
| refresh_tokens | id, user_id, token_hash, expires_at | 로그아웃 시 삭제 |
| rooms | id, name, description, is_public, invite_code(uq), owner_id, max_members, thumbnail_url, created_at | |
| room_members | room_id, user_id, role(OWNER/MEMBER), joined_at | PK (room_id, user_id) |
| mission_templates | id, content, reward_item_id, active | APP 미션 풀, 시드 데이터 |
| missions | id, room_id, type(APP/MEMBER), template_id, content, reward_type(ITEM/COIN), reward_item_id, reward_coins, created_by, date, due_at, created_at | uq (room_id, date) where type=APP |
| proofs | id, mission_id, user_id, media_url, thumbnail_url, media_type, text, status(PENDING/APPROVED/REJECTED/EXPIRED), finalized_at, created_at | uq (mission_id, user_id) where status in (PENDING, APPROVED) |
| proof_votes | proof_id, voter_id, vote(APPROVE/REJECT), created_at | PK (proof_id, voter_id) |
| proof_reactions | proof_id, user_id, emoji | PK (proof_id, user_id, emoji) |
| proof_views | proof_id, user_id, viewed_at | 스토리 읽음 표시 |
| items | id, name, type(AVATAR/FURNITURE), category, price, image_url, obtainable_by | 시드 데이터 |
| user_items | id, user_id, item_id, acquired_by(SHOP/MISSION), acquired_at | |
| coin_transactions | id, user_id, amount, balance_after, reason, ref_id, created_at | |
| room_decorations | id, room_id, item_id, owner_id, x, y, z, flipped | background는 rooms.background_item_id |
| notifications | id, user_id, type, title, body, ref_type, ref_id, read_at, created_at | 후순위 |

---

## 11. MVP 우선순위

제안서 기준 순서이며, 각 단계가 끝날 때마다 데모 가능한 상태를 유지합니다.

| 순서 | 범위 | 브랜치 | 목표 주차 |
|---|---|---|---|
| 0 | 프로젝트 골격, DB 연결, 공통 응답/에러 처리 | `chore/project-setup` | 11월 2주 |
| 1 | 인증, 사용자 (2, 3장) | `feat/auth` | 11월 2주 |
| 2 | 룸 (4장) | `feat/room` | 11월 3주 |
| 3 | 미션 + 인증 제출 + 승인/보상 지급 (5, 6장) | `feat/mission`, `feat/proof` | 11월 3~4주 |
| 4 | 상점, 보유 아이템 (7장) | `feat/reward` | 12월 1주 |
| 5 | 데코 (8장) | `feat/decor` | 12월 1주 |
| 6 | 알림 (9장), 배포 | `feat/notification`, `chore/deploy` | 12월 1~2주 |

---

## 변경 이력

| 버전 | 날짜 | 내용 |
|---|---|---|
| v0.1 | 2026-11-05 | 초안 작성 |
| v0.1.1 | 2026-11-08 | 공개 룸 목록 기본 정렬을 인기 점수로 변경, 점수 계산 규칙 추가 (4.1) |
