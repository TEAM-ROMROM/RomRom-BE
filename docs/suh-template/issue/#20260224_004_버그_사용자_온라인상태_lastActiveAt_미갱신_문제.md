❗[버그][회원][채팅] 사용자 온라인 상태(isOnline) 및 lastActiveAt 갱신/반환 로직 미비

🗒️ 설명
---

- 프론트에서 사용자의 "현재 활동중" 여부를 확인하는 데 어려움이 있음
- 백엔드에서 `isOnline`과 `lastActiveAt` 값이 정확하게 제공되지 않아, 프론트에서 "4시간 전" 등 잘못된 활동 시간이 표시되는 문제 발생
- 현재 `isOnline`은 `@Transient` 필드로 DB에 저장되지 않으며, `lastActiveAt` 기준 90초 이내일 때만 `true`로 계산됨
- `setOnlineIfActiveWithin90Seconds()` 메서드가 채팅방 목록 조회(`convertToDetailDto`) 시에만 호출되고 있어, 다른 API 응답에서는 `isOnline`이 항상 `false`로 반환될 수 있음
- `lastActiveAt` 갱신은 프론트가 `/api/members/heartbeat`를 주기적으로 호출해야만 이루어지는데, 호출 주기나 조건이 프론트와 명확히 합의되지 않은 상태

🔄 재현 방법
---

1. 앱에서 활발하게 활동 중인 사용자 A가 있음
2. 다른 사용자 B가 채팅방 목록을 조회하면, 사용자 A의 `isOnline`이 `false`이고 `lastActiveAt`이 "4시간 전"으로 표시됨
3. 프론트에서 `isOnline == false`일 때 `lastActiveAt`을 표시하지 않는 로직이 있어, 활동 시간 자체가 보이지 않는 경우도 발생

📸 참고 자료
---

프론트 개발자 피드백:
> "online은 없어도 lastActiveAt은 왜 업데이트 안 되지?"
> "4시간 전 막 이렇게 뜨는데"
> "if online==false lastActiveAt 업데이트 안 함 이런 거 있는 건가?"

✅ 예상 동작
---

- 사용자가 앱을 활발히 사용 중이면 `lastActiveAt`이 실시간에 가깝게 갱신되어야 함
- `isOnline` 값이 `lastActiveAt` 기준으로 정확하게 계산되어 채팅방 목록 등 관련 API 응답에 포함되어야 함
- 프론트에서 heartbeat 호출 주기, 조건 등이 백엔드와 명확히 합의되어야 함
- `isOnline`과 `lastActiveAt`이 반환되는 API 목록과 각 필드의 의미가 프론트에 정확히 문서화되어야 함

⚙️ 현재 백엔드 구조 정리
---

| 항목 | 현재 상태 |
|------|----------|
| `lastActiveAt` | DB 저장 O, `/api/members/heartbeat` POST 호출 시 갱신 (60초 간격 제한) |
| `isOnline` | DB 저장 X (`@Transient`), `lastActiveAt`이 90초 이내면 `true` |
| `isOnline` 계산 시점 | 채팅방 목록 조회(`convertToDetailDto`) 시에만 `setOnlineIfActiveWithin90Seconds()` 호출 |
| heartbeat API | `POST /api/members/heartbeat` (프론트가 주기적으로 호출해야 함) |

⚙️ 작업 내용
---

- heartbeat 호출 주기 및 조건을 프론트와 합의 (예: 앱 포그라운드 시 30~60초 간격 호출)
- `isOnline` 계산 로직이 필요한 모든 API 응답에서 `setOnlineIfActiveWithin90Seconds()` 호출 보장
- `isOnline`, `lastActiveAt` 필드가 포함되는 API 응답 목록 정리 및 프론트에 공유
- 필요 시 `isOnline` 판정 기준 시간(현재 90초) 조정 검토

🙋‍♂️ 담당자
---

- 백엔드: 이름
- 프론트엔드: 이름
- 디자인: 이름
