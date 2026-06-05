# 채팅방 Soft Delete + 배치 아카이브 구현 플랜 (#750)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 채팅방 완전 삭제 시 PostgreSQL·MongoDB 비원자 처리로 인한 데이터 불일치(#750)를, 동기 흐름의 Hard Delete를 제거하고 `ChatRoom.deletedAt` soft delete + 관리자 관리 API + 배치 아카이브·물리삭제로 전환하여 근본 해결한다.

**Architecture:** 나가기/관리자삭제는 PG 단일 트랜잭션으로 `deletedAt`만 표시(원자적). 실제 물리 삭제는 30일 경과 후 배치가 한 DB씩 처리하되, 삭제 전 채팅 로그를 `.json.gz`(이미지는 URL 참조만)로 호스트 마운트 `backup/`에 아카이브한다. 관리자는 삭제 대기 방을 조회/추출/즉시삭제할 수 있다.

**Tech Stack:** Spring Boot (멀티모듈), JPA(PostgreSQL), MongoDB, Flyway, `@Scheduled`, Java `GZIPOutputStream`, Jackson, suh-logger 테스트 컨벤션.

---

## 테스트 컨벤션 (이 프로젝트 고유 — 반드시 준수)

이 프로젝트는 표준 JUnit 단위 테스트(개별 `@Test` 다수)가 아니라 **통합 테스트 + 한글 시나리오 메서드** 패턴을 쓴다. 참고: `RomRom-Application/src/test/java/com/romrom/application/service/AdminReportServiceTest.java`.

```java
@SpringBootTest(classes = RomBackApplication.class)
@ActiveProfiles("dev")
@Slf4j
class XxxServiceTest {
  @Autowired XxxService xxxService;

  @Test
  public void mainTest() {
    lineLog("테스트시작");
    lineLog(null);
    timeLog(this::시나리오메서드명_한글_테스트);
    lineLog(null);
    lineLog("테스트종료");
  }

  private void 시나리오메서드명_한글_테스트() {
    // given / when / then + Assertions
  }
}
```

import: `static me.suhsaechan.suhlogger.util.SuhLogger.{lineLog, superLog, timeLog}`, `org.junit.jupiter.api.Assertions`.

따라서 각 태스크의 "테스트" 단계는 위 패턴의 시나리오 메서드를 `mainTest()`에 추가하는 형태로 작성한다. 표준 red-green-refactor 대신, **시나리오 메서드 작성 → 전체 `mainTest` 실행 → 통과 확인** 순서를 따른다.

---

## File Structure

**생성:**
- `RomRom-Web/src/main/resources/db/migration/V1_4_64__add_chat_room_deleted_at.sql` — `deleted_at` 컬럼 + 인덱스
- `RomRom-Application/src/main/java/com/romrom/application/service/ChatRoomArchiveService.java` — JSON 조립 + gzip + backup/ 저장 (export·배치 공용)
- `RomRom-Application/src/main/java/com/romrom/application/service/AdminChatRoomService.java` — 관리자 목록/상세/추출/즉시삭제
- `RomRom-Application/src/main/java/com/romrom/application/scheduler/ChatRoomCleanupScheduler.java` — 30일 경과 방 아카이브→물리삭제 배치
- `RomRom-Application/src/main/java/com/romrom/application/dto/ChatRoomArchiveDto.java` — 아카이브 JSON 직렬화 모델
- `RomRom-Application/src/test/java/com/romrom/application/service/AdminChatRoomServiceTest.java` — 통합 테스트

**수정:**
- `RomRom-Domain-Chat/.../entity/postgres/ChatRoom.java` — `deletedAt` 필드 + `softDelete()`
- `RomRom-Domain-Chat/.../repository/postgres/ChatRoomRepository.java` — 목록 쿼리 `deletedAt IS NULL` 필터 + 청소 대상 조회 메서드
- `RomRom-Domain-Chat/.../service/ChatRoomService.java` — 물리삭제 호출 → soft delete 교체
- `RomRom-Application/.../dto/AdminRequest.java` — `chatRoomId` 필드 (pageNumber/pageSize/sortBy는 기존 재사용)
- `RomRom-Application/.../dto/AdminResponse.java` — 채팅방 목록/상세 필드
- `RomRom-Web/.../controller/api/AdminApiController.java` — 엔드포인트 4종 + Docs ApiChangeLog
- `RomRom-Domain-Chat/.../docs/ChatControllerDocs.java` (존재 시) — 삭제 동작 변경 description

---

## Task 1: ChatRoom 엔티티에 deletedAt 추가 + Flyway 마이그레이션

**Files:**
- Create: `RomRom-Web/src/main/resources/db/migration/V1_4_64__add_chat_room_deleted_at.sql`
- Modify: `RomRom-Domain-Chat/src/main/java/com/romrom/chat/entity/postgres/ChatRoom.java`

- [ ] **Step 1: Flyway 마이그레이션 파일 작성**

`V1_4_64__add_chat_room_deleted_at.sql`:
```sql
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'chat_room')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'chat_room' AND column_name = 'deleted_at') THEN
        ALTER TABLE chat_room ADD COLUMN deleted_at TIMESTAMP;
        CREATE INDEX IF NOT EXISTS idx_chat_room_deleted_at ON chat_room (deleted_at);
    END IF;
EXCEPTION
    WHEN OTHERS THEN RAISE WARNING '오류 발생: %', SQLERRM;
END $$;
```

- [ ] **Step 2: ChatRoom 엔티티에 필드 + 메서드 추가**

`ChatRoom.java`의 `tradeRequestHistory` 필드 아래, `isMember` 메서드 위에 추가:
```java
  // soft delete 시각. null이면 활성 방, non-null이면 배치 청소 대기 상태
  private LocalDateTime deletedAt;

  // soft delete 표시 (멱등 — 이미 표시된 방이면 무시하여 중복 나가기/삭제 방어)
  public void softDelete() {
    if (this.deletedAt == null) {
      this.deletedAt = LocalDateTime.now();
    }
  }
```
import 추가: `import java.time.LocalDateTime;`

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :RomRom-Domain-Chat:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add RomRom-Web/src/main/resources/db/migration/V1_4_64__add_chat_room_deleted_at.sql \
        RomRom-Domain-Chat/src/main/java/com/romrom/chat/entity/postgres/ChatRoom.java
git commit -m "채팅방_완전_삭제_시_PostgreSQL_MongoDB_간_비원자_처리로_데이터_불일치_가능 : feat : #750 ChatRoom deletedAt soft delete 컬럼 및 필드 추가 https://github.com/TEAM-ROMROM/RomRom-BE/issues/750"
```

---

## Task 2: ChatRoomRepository — 목록 필터 + 청소 대상 조회

**Files:**
- Modify: `RomRom-Domain-Chat/src/main/java/com/romrom/chat/repository/postgres/ChatRoomRepository.java`

- [ ] **Step 1: 목록 쿼리 2개에 `deletedAt IS NULL` 필터 추가**

`findByTradeReceiverOrTradeSender`를 아래로 교체 (WHERE·countQuery 둘 다 `AND c.deletedAt IS NULL` 추가):
```java
  @Query(value = "SELECT c FROM ChatRoom c " +
      "JOIN FETCH c.tradeReceiver JOIN FETCH c.tradeSender " +
      "JOIN FETCH c.tradeRequestHistory trh " +
      "JOIN FETCH trh.takeItem " +
      "JOIN FETCH trh.giveItem " +
      "WHERE (c.tradeReceiver = :tradeReceiver OR c.tradeSender = :tradeSender) " +
      "AND c.deletedAt IS NULL",
      countQuery = "SELECT count(c) FROM ChatRoom c " +
          "WHERE (c.tradeReceiver = :tradeReceiver OR c.tradeSender = :tradeSender) " +
          "AND c.deletedAt IS NULL")
  Page<ChatRoom> findByTradeReceiverOrTradeSender(Member tradeReceiver, Member tradeSender, Pageable pageable);
```

`findByMemberAndItemId`도 WHERE·countQuery에 `AND c.deletedAt IS NULL` 추가:
```java
  @Query(value = "SELECT c FROM ChatRoom c " +
      "JOIN FETCH c.tradeReceiver JOIN FETCH c.tradeSender " +
      "JOIN FETCH c.tradeRequestHistory trh " +
      "JOIN FETCH trh.takeItem " +
      "JOIN FETCH trh.giveItem " +
      "WHERE (c.tradeReceiver = :member OR c.tradeSender = :member) " +
      "AND (trh.takeItem.itemId = :itemId OR trh.giveItem.itemId = :itemId) " +
      "AND c.deletedAt IS NULL",
      countQuery = "SELECT count(c) FROM ChatRoom c " +
          "JOIN c.tradeRequestHistory trh " +
          "WHERE (c.tradeReceiver = :member OR c.tradeSender = :member) " +
          "AND (trh.takeItem.itemId = :itemId OR trh.giveItem.itemId = :itemId) " +
          "AND c.deletedAt IS NULL")
  Page<ChatRoom> findByMemberAndItemId(@Param("member") Member member, @Param("itemId") UUID itemId, Pageable pageable);
```

- [ ] **Step 2: 관리자용 soft-delete 목록 + 배치 청소 대상 조회 메서드 추가**

인터페이스 하단(`findAllByTradeSender_...` 아래)에 추가:
```java
  // 관리자: soft-delete된(청소 대기) 방 목록 페이지 조회
  @Query(value = "SELECT c FROM ChatRoom c " +
      "JOIN FETCH c.tradeReceiver JOIN FETCH c.tradeSender JOIN FETCH c.tradeRequestHistory " +
      "WHERE c.deletedAt IS NOT NULL",
      countQuery = "SELECT count(c) FROM ChatRoom c WHERE c.deletedAt IS NOT NULL")
  Page<ChatRoom> findByDeletedAtIsNotNull(Pageable pageable);

  // 배치: 유예 기간(deletedAt < threshold) 지난 청소 대상 방 조회
  @Query("SELECT c FROM ChatRoom c " +
      "JOIN FETCH c.tradeReceiver JOIN FETCH c.tradeSender JOIN FETCH c.tradeRequestHistory " +
      "WHERE c.deletedAt IS NOT NULL AND c.deletedAt < :threshold")
  List<ChatRoom> findCleanupTargets(@Param("threshold") LocalDateTime threshold);
```
import 추가: `import java.time.LocalDateTime;`

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :RomRom-Domain-Chat:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add RomRom-Domain-Chat/src/main/java/com/romrom/chat/repository/postgres/ChatRoomRepository.java
git commit -m "채팅방_완전_삭제_시_PostgreSQL_MongoDB_간_비원자_처리로_데이터_불일치_가능 : feat : #750 채팅방 목록 deletedAt 필터 및 청소 대상 조회 쿼리 추가 https://github.com/TEAM-ROMROM/RomRom-BE/issues/750"
```

---

## Task 3: ChatRoomService — 물리삭제를 soft delete로 교체

**Files:**
- Modify: `RomRom-Domain-Chat/src/main/java/com/romrom/chat/service/ChatRoomService.java`

**주의:** `executeHardDelete()`의 실제 물리삭제 로직(3개 repository deleteBy)은 **삭제하지 말고** Task 5의 배치가 재사용할 수 있도록 보존 방식을 정한다. 여기서는 동기 호출 지점만 soft delete로 바꾼다. 물리삭제 로직은 Task 5에서 `ChatRoomCleanupScheduler`(또는 별도 deleter)로 이동시키므로, 본 태스크에서는 `executeHardDelete` 본문을 그대로 두되 호출부만 교체한다.

- [ ] **Step 1: `handleRoomExit`의 hard delete 호출을 soft delete로 교체**

`handleRoomExit` 내 `if (opponentState.isDeleted())` 블록(현 `:346-347`)을 아래로 교체:
```java
    if (opponentState.isDeleted()) {      // 상대방도 이미 나감 -> soft delete 표시 (배치가 나중에 아카이브 후 물리삭제)
      ChatRoom managedRoom = chatRoomRepository.findById(roomId)
          .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));
      managedRoom.softDelete();
      chatRoomRepository.save(managedRoom);
      log.debug("양쪽 모두 나가 채팅방을 soft delete 표시했습니다. roomId={}", roomId);
    } else {                              // 상대방은 아직 남아있음 -> 시스템 메시지만 전송
      log.debug("상대방이 남아있어 내 상태만 삭제 표시했습니다. roomId={}, myId={}", roomId, myId);
      chatMessageService.sendSystemMessage(room, myId, opponentState);
    }
```
(`ErrorCode.CHAT_ROOM_NOT_FOUND`가 없으면 기존 채팅 관련 ErrorCode 확인 후 적절한 것 사용. `room` 파라미터가 이미 managed 상태라면 재조회 없이 `room.softDelete()` 사용 가능 — `handleRoomExit` 진입부에서 받은 `room`의 영속성 컨텍스트 상태 확인 후 단순화.)

- [ ] **Step 2: `adminForceDeleteChatRoom`의 hard delete 호출을 soft delete로 교체**

`adminForceDeleteChatRoom`(현 `:359-368`)을 아래로 교체:
```java
  @Transactional
  public void adminForceDeleteChatRoom(UUID chatRoomId) {
    ChatRoom room = chatRoomRepository.findById(chatRoomId).orElse(null);
    if (room == null) {
      log.warn("관리자 강제 채팅방 삭제 요청: 존재하지 않는 chatRoomId={}", chatRoomId);
      return;
    }
    room.softDelete();
    chatRoomRepository.save(room);
    log.info("관리자 강제 채팅방 soft delete 완료: chatRoomId={}", chatRoomId);
  }
```

- [ ] **Step 3: `executeHardDelete` private 메서드를 public 물리삭제 메서드로 노출 (배치/관리자 재사용)**

`executeHardDelete`의 접근제어자를 `public`으로 바꾸고 이름을 `physicalDelete`로 변경 (의미 명확화). 배치/즉시삭제가 호출한다:
```java
  // 실제 물리 삭제 (배치 청소 / 관리자 즉시삭제에서 호출). 멱등.
  @Transactional
  public void physicalDelete(UUID roomId) {
    if (!chatRoomRepository.existsById(roomId)) {
      log.debug("이미 물리 삭제된 채팅방입니다. 건너뜁니다. roomId={}", roomId);
      return;
    }
    // Mongo 먼저, PG 마지막 (중간 실패 시 deletedAt 남아 다음 배치 재시도)
    chatMessageRepository.deleteByChatRoomId(roomId);
    chatUserStateRepository.deleteAllByChatRoomId(roomId);
    chatRoomRepository.deleteById(roomId);
    log.debug("채팅방 물리 삭제 완료. roomId={}", roomId);
  }
```
기존 `executeHardDelete` 호출부가 더 이상 없는지 확인(Step 1·2에서 제거됨).

- [ ] **Step 4: `buildChatRoomDetailResponse` 삭제방 필터(`-1L`) 영향 확인**

`:304-310`의 `-1L` 필터는 한쪽만 나간 soft 상태(`ChatUserState.removedAt`) 처리용이므로 **유지**한다. `deletedAt` 방은 Task 2의 쿼리 필터에서 이미 제외되므로 추가 변경 불필요. 주석만 보강:
```java
        .filter(chatRoom -> {
          Long count = unreadCounts.get(chatRoom.getChatRoomId());
          // 한쪽만 나간 방(ChatUserState 기준 -1L) 필터링. 양쪽 나간 deletedAt 방은 쿼리에서 이미 제외됨
          return count != null && count != -1L;
        })
```

- [ ] **Step 5: 컴파일 확인**

Run: `./gradlew :RomRom-Domain-Chat:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add RomRom-Domain-Chat/src/main/java/com/romrom/chat/service/ChatRoomService.java
git commit -m "채팅방_완전_삭제_시_PostgreSQL_MongoDB_간_비원자_처리로_데이터_불일치_가능 : feat : #750 채팅방 나가기/관리자삭제를 soft delete로 전환하고 물리삭제 메서드 분리 https://github.com/TEAM-ROMROM/RomRom-BE/issues/750"
```

---

## Task 4: ChatRoomArchiveService — JSON 조립 + gzip + backup 저장

**Files:**
- Create: `RomRom-Application/src/main/java/com/romrom/application/dto/ChatRoomArchiveDto.java`
- Create: `RomRom-Application/src/main/java/com/romrom/application/service/ChatRoomArchiveService.java`

- [ ] **Step 1: 아카이브 DTO 작성**

`ChatRoomArchiveDto.java`:
```java
package com.romrom.application.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 채팅방 아카이브 직렬화 모델. 이미지는 imageUrls(URL 참조)만 포함하고 바이너리는 백업하지 않는다.
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class ChatRoomArchiveDto {
  private UUID chatRoomId;
  private UUID tradeReceiverId;
  private UUID tradeSenderId;
  private LocalDateTime deletedAt;
  private LocalDateTime archivedAt;
  private List<MessageEntry> messages;

  @Getter @Builder @NoArgsConstructor @AllArgsConstructor
  public static class MessageEntry {
    private String chatMessageId;
    private UUID senderId;
    private UUID recipientId;
    private String content;
    private List<String> imageUrls;   // URL 참조만
    private String type;
    private LocalDateTime createdDate;
  }
}
```

- [ ] **Step 2: 아카이브 서비스 작성**

`ChatRoomArchiveService.java`:
```java
package com.romrom.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.romrom.application.dto.ChatRoomArchiveDto;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.chat.repository.mongo.ChatMessageRepository;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// 채팅방 데이터를 JSON 조립 후 gzip 압축하여 backup/ 경로에 저장. 관리자 export와 배치 청소가 공용으로 사용.
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatRoomArchiveService {

  private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

  private final ChatMessageRepository chatMessageRepository;
  private final ObjectMapper objectMapper;

  // 호스트 마운트 백업 경로 (application.yml 에 chat.archive.backup-dir 설정. 미설정 시 ./backup/chat-rooms)
  @Value("${chat.archive.backup-dir:./backup/chat-rooms}")
  private String backupDir;

  // 채팅방을 .json.gz 로 backup 디렉터리에 저장하고 저장된 파일 경로를 반환한다.
  public Path archiveToFile(ChatRoom room) throws IOException {
    ChatRoomArchiveDto archiveDto = buildArchiveDto(room);
    byte[] jsonBytes = objectMapper.writeValueAsBytes(archiveDto);

    Path dir = Paths.get(backupDir);
    Files.createDirectories(dir);
    String fileName = room.getChatRoomId() + "_"
        + (room.getDeletedAt() != null ? room.getDeletedAt() : LocalDateTime.now()).format(FILE_TIMESTAMP)
        + ".json.gz";
    Path filePath = dir.resolve(fileName);

    try (OutputStream fileOut = Files.newOutputStream(filePath);
         GZIPOutputStream gzipOut = new GZIPOutputStream(fileOut)) {
      gzipOut.write(jsonBytes);
    }
    log.info("채팅방 아카이브 저장 완료: roomId={}, path={}, bytes={}", room.getChatRoomId(), filePath, jsonBytes.length);
    return filePath;
  }

  // 관리자 export 용: gzip 압축된 바이트 배열을 메모리로 반환 (다운로드 스트림용)
  public byte[] archiveToGzipBytes(ChatRoom room) throws IOException {
    ChatRoomArchiveDto archiveDto = buildArchiveDto(room);
    byte[] jsonBytes = objectMapper.writeValueAsBytes(archiveDto);
    java.io.ByteArrayOutputStream byteOut = new java.io.ByteArrayOutputStream();
    try (GZIPOutputStream gzipOut = new GZIPOutputStream(byteOut)) {
      gzipOut.write(jsonBytes);
    }
    return byteOut.toByteArray();
  }

  private ChatRoomArchiveDto buildArchiveDto(ChatRoom room) {
    UUID roomId = room.getChatRoomId();
    List<ChatMessage> messages = chatMessageRepository
        .findByChatRoomIdOrderByCreatedDateDesc(roomId, org.springframework.data.domain.Pageable.unpaged())
        .getContent();

    List<ChatRoomArchiveDto.MessageEntry> messageEntries = messages.stream()
        .map(message -> ChatRoomArchiveDto.MessageEntry.builder()
            .chatMessageId(message.getChatMessageId())
            .senderId(message.getSenderId())
            .recipientId(message.getRecipientId())
            .content(message.getContent())
            .imageUrls(message.getImageUrls())   // URL 참조만 (바이너리 미백업)
            .type(message.getType() != null ? message.getType().name() : null)
            .createdDate(message.getCreatedDate())
            .build())
        .collect(Collectors.toList());

    return ChatRoomArchiveDto.builder()
        .chatRoomId(roomId)
        .tradeReceiverId(room.getTradeReceiver().getMemberId())
        .tradeSenderId(room.getTradeSender().getMemberId())
        .deletedAt(room.getDeletedAt())
        .archivedAt(LocalDateTime.now())
        .messages(messageEntries)
        .build();
  }
}
```
**확인 필요:** `ChatMessage.getCreatedDate()`가 `BaseMongoEntity`에서 `LocalDateTime`을 반환하는지 확인(아니면 타입 맞춤). `MessageType` enum 패키지 import 불필요(`.name()`만 사용). `findByChatRoomIdOrderByCreatedDateDesc`가 `Slice` 반환이므로 `Pageable.unpaged()`로 전체 조회 가능 여부 확인 — 불가하면 `findAllByChatRoomId(UUID)` 메서드를 `ChatMessageRepository`에 추가.

- [ ] **Step 3: (필요 시) ChatMessageRepository에 전체 조회 메서드 추가**

`Pageable.unpaged()`로 Slice 전체 조회가 안 되면 `ChatMessageRepository`에 추가:
```java
  List<ChatMessage> findAllByChatRoomId(UUID chatRoomId);
```
그리고 Step 2의 `buildArchiveDto`에서 이 메서드 사용하도록 교체.

- [ ] **Step 4: 컴파일 확인**

Run: `./gradlew :RomRom-Application:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 아카이브 라운드트립 테스트 작성**

`AdminChatRoomServiceTest.java`를 Task 7에서 만들지만, 아카이브 검증 시나리오는 여기서 추가한다. 임시로 `ChatRoomArchiveServiceTest.java` 생성:
```java
package com.romrom.application.service;

import static me.suhsaechan.suhlogger.util.SuhLogger.lineLog;
import static me.suhsaechan.suhlogger.util.SuhLogger.timeLog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.romrom.application.dto.ChatRoomArchiveDto;
import java.io.ByteArrayInputStream;
import java.util.zip.GZIPInputStream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import com.romrom.web.RomBackApplication;

@SpringBootTest(classes = RomBackApplication.class)
@ActiveProfiles("dev")
@Slf4j
class ChatRoomArchiveServiceTest {

  @Autowired ChatRoomArchiveService chatRoomArchiveService;
  @Autowired ObjectMapper objectMapper;

  @Test
  public void mainTest() {
    lineLog("테스트시작");
    lineLog(null);
    timeLog(this::gzip_압축_해제_라운드트립_JSON_복원_테스트);
    lineLog(null);
    lineLog("테스트종료");
  }

  // 실제 ChatRoom 없이 gzip→json 복원만 검증 (DB 의존 최소화). 실제 방 아카이브는 Task 7 통합테스트에서 검증.
  private void gzip_압축_해제_라운드트립_JSON_복원_테스트() {
    try {
      ChatRoomArchiveDto dto = ChatRoomArchiveDto.builder()
          .chatRoomId(java.util.UUID.randomUUID())
          .archivedAt(java.time.LocalDateTime.now())
          .messages(java.util.List.of())
          .build();
      byte[] jsonBytes = objectMapper.writeValueAsBytes(dto);
      java.io.ByteArrayOutputStream byteOut = new java.io.ByteArrayOutputStream();
      try (java.util.zip.GZIPOutputStream gzipOut = new java.util.zip.GZIPOutputStream(byteOut)) {
        gzipOut.write(jsonBytes);
      }
      byte[] gzipped = byteOut.toByteArray();
      Assertions.assertTrue(gzipped.length > 0);

      try (GZIPInputStream gzipIn = new GZIPInputStream(new ByteArrayInputStream(gzipped))) {
        ChatRoomArchiveDto restored = objectMapper.readValue(gzipIn.readAllBytes(), ChatRoomArchiveDto.class);
        Assertions.assertEquals(dto.getChatRoomId(), restored.getChatRoomId());
      }
    } catch (Exception e) {
      Assertions.fail("라운드트립 실패: " + e.getMessage());
    }
  }
}
```

- [ ] **Step 6: 테스트 실행**

Run: `./gradlew :RomRom-Application:test --tests "com.romrom.application.service.ChatRoomArchiveServiceTest"`
Expected: PASS (`gzip_압축_해제_라운드트립_JSON_복원_테스트` 통과)

- [ ] **Step 7: Commit**

```bash
git add RomRom-Application/src/main/java/com/romrom/application/dto/ChatRoomArchiveDto.java \
        RomRom-Application/src/main/java/com/romrom/application/service/ChatRoomArchiveService.java \
        RomRom-Application/src/test/java/com/romrom/application/service/ChatRoomArchiveServiceTest.java
git commit -m "채팅방_완전_삭제_시_PostgreSQL_MongoDB_간_비원자_처리로_데이터_불일치_가능 : feat : #750 채팅방 아카이브 서비스(JSON+gzip+backup) 추가 https://github.com/TEAM-ROMROM/RomRom-BE/issues/750"
```

---

## Task 5: ChatRoomCleanupScheduler — 배치 아카이브 후 물리삭제

**Files:**
- Create: `RomRom-Application/src/main/java/com/romrom/application/scheduler/ChatRoomCleanupScheduler.java`

- [ ] **Step 1: 스케줄러 작성** (`OrphanImageCleanupScheduler` 패턴)

```java
package com.romrom.application.scheduler;

import com.romrom.application.service.ChatRoomArchiveService;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.chat.repository.postgres.ChatRoomRepository;
import com.romrom.chat.service.ChatRoomService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * soft-delete된 채팅방 자동 정리 스케줄러 (#750)
 * - deletedAt 으로부터 30일 경과한 방을 아카이브(.json.gz) 후 물리 삭제
 * - 아카이브 실패 시 해당 방은 물리삭제를 건너뛰어, 다음 배치가 재시도 (백업 없는 삭제 방지)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatRoomCleanupScheduler {

  private static final int ARCHIVE_RETENTION_DAYS = 30;

  private final ChatRoomRepository chatRoomRepository;
  private final ChatRoomArchiveService chatRoomArchiveService;
  private final ChatRoomService chatRoomService;

  // TODO: 운영 검증 후 활성화 (OrphanImageCleanupScheduler 동일 패턴)
  // @Scheduled(cron = "0 0 4 * * *")  // 매일 새벽 4시
  public void cleanupDeletedChatRooms() {
    log.info("채팅방 정리 스케줄러 시작");
    LocalDateTime threshold = LocalDateTime.now().minusDays(ARCHIVE_RETENTION_DAYS);
    List<ChatRoom> targets = chatRoomRepository.findCleanupTargets(threshold);
    log.info("채팅방 정리 대상 수: {}", targets.size());

    int archivedCount = 0;
    int deletedCount = 0;
    for (ChatRoom room : targets) {
      try {
        chatRoomArchiveService.archiveToFile(room);   // 실패 시 예외 → 물리삭제 스킵
        archivedCount++;
        chatRoomService.physicalDelete(room.getChatRoomId());
        deletedCount++;
      } catch (Exception cleanupException) {
        log.warn("채팅방 정리 실패(다음 배치 재시도): roomId={}, error={}",
            room.getChatRoomId(), cleanupException.getMessage());
      }
    }
    log.info("채팅방 정리 스케줄러 완료: 대상={}, 아카이브={}, 삭제={}", targets.size(), archivedCount, deletedCount);
  }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew :RomRom-Application:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add RomRom-Application/src/main/java/com/romrom/application/scheduler/ChatRoomCleanupScheduler.java
git commit -m "채팅방_완전_삭제_시_PostgreSQL_MongoDB_간_비원자_처리로_데이터_불일치_가능 : feat : #750 soft-delete 채팅방 배치 아카이브 후 물리삭제 스케줄러 추가 https://github.com/TEAM-ROMROM/RomRom-BE/issues/750"
```

---

## Task 6: AdminChatRoomService + AdminRequest/Response 필드

**Files:**
- Create: `RomRom-Application/src/main/java/com/romrom/application/service/AdminChatRoomService.java`
- Modify: `RomRom-Application/src/main/java/com/romrom/application/dto/AdminRequest.java`
- Modify: `RomRom-Application/src/main/java/com/romrom/application/dto/AdminResponse.java`

- [ ] **Step 1: AdminRequest에 chatRoomId 필드 추가**

`AdminRequest.java`의 `itemId` 필드 아래에 추가 (pageNumber/pageSize/sortBy는 기존 재사용):
```java
    @Schema(description = "채팅방 ID (채팅방 상세/추출/즉시삭제 시 사용)")
    private UUID chatRoomId;
```

- [ ] **Step 2: AdminResponse에 채팅방 목록/상세 필드 추가**

`AdminResponse.java`에 추가 (Entity 그대로 담기 — 프로젝트 Response 원칙). import에 `ChatMessage` 추가:
```java
import com.romrom.chat.entity.mongo.ChatMessage;
```
필드 추가:
```java
    @Schema(description = "soft-delete된(청소 대기) 채팅방 목록")
    private Page<ChatRoom> deletedChatRooms;

    @Schema(description = "채팅방 상세 - 채팅방 엔티티")
    private ChatRoom chatRoom;

    @Schema(description = "채팅방 상세 - 메시지 목록")
    private List<ChatMessage> chatMessages;
```

- [ ] **Step 3: AdminChatRoomService 작성**

```java
package com.romrom.application.service;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.chat.repository.mongo.ChatMessageRepository;
import com.romrom.chat.repository.postgres.ChatRoomRepository;
import com.romrom.chat.service.ChatRoomService;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 관리자: soft-delete된 채팅방 목록/상세/추출/즉시삭제
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminChatRoomService {

  private final ChatRoomRepository chatRoomRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final ChatRoomArchiveService chatRoomArchiveService;
  private final ChatRoomService chatRoomService;

  // 1. soft-delete된 방 목록 (deletedAt IS NOT NULL)
  @Transactional(readOnly = true)
  public AdminResponse getDeletedChatRooms(AdminRequest request) {
    Pageable pageable = PageRequest.of(request.getPageNumber(), request.getPageSize(),
        Sort.by(request.getSortDirection(), "deletedAt"));
    Page<ChatRoom> deletedChatRooms = chatRoomRepository.findByDeletedAtIsNotNull(pageable);
    return AdminResponse.builder()
        .deletedChatRooms(deletedChatRooms)
        .totalCount(deletedChatRooms.getTotalElements())
        .build();
  }

  // 2. 채팅방 상세 (메시지 전체)
  @Transactional(readOnly = true)
  public AdminResponse getChatRoomDetail(AdminRequest request) {
    UUID chatRoomId = request.getChatRoomId();
    ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
        .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    List<ChatMessage> chatMessages = chatMessageRepository.findAllByChatRoomId(chatRoomId);
    return AdminResponse.builder()
        .chatRoom(chatRoom)
        .chatMessages(chatMessages)
        .build();
  }

  // 3. 추출(export): gzip 바이트 반환 (컨트롤러가 다운로드 스트림으로 전달)
  @Transactional(readOnly = true)
  public byte[] exportChatRoom(AdminRequest request) {
    ChatRoom chatRoom = chatRoomRepository.findById(request.getChatRoomId())
        .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    try {
      return chatRoomArchiveService.archiveToGzipBytes(chatRoom);
    } catch (IOException e) {
      log.error("채팅방 추출 실패: chatRoomId={}, error={}", request.getChatRoomId(), e.getMessage());
      throw new CustomException(ErrorCode.CHAT_ROOM_EXPORT_FAILED);
    }
  }

  // 4. 수동 즉시 삭제: 아카이브(backup 저장) 후 물리삭제
  @Transactional
  public void forceDeleteChatRoom(AdminRequest request) {
    UUID chatRoomId = request.getChatRoomId();
    ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
        .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    try {
      chatRoomArchiveService.archiveToFile(chatRoom);   // 백업 실패 시 삭제 중단
    } catch (IOException e) {
      log.error("즉시 삭제 전 아카이브 실패로 삭제 중단: chatRoomId={}, error={}", chatRoomId, e.getMessage());
      throw new CustomException(ErrorCode.CHAT_ROOM_EXPORT_FAILED);
    }
    chatRoomService.physicalDelete(chatRoomId);
    log.info("관리자 즉시 삭제 완료: chatRoomId={}", chatRoomId);
  }
}
```
**확인 필요:** `ErrorCode.CHAT_ROOM_NOT_FOUND`, `CHAT_ROOM_EXPORT_FAILED`가 없으면 `ErrorCode` enum에 추가 (기존 ErrorCode 정의 파일 확인 후 동일 형식으로). `findAllByChatRoomId`가 Task 4 Step 3에서 추가됐는지 확인.

- [ ] **Step 4: 컴파일 확인**

Run: `./gradlew :RomRom-Application:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add RomRom-Application/src/main/java/com/romrom/application/service/AdminChatRoomService.java \
        RomRom-Application/src/main/java/com/romrom/application/dto/AdminRequest.java \
        RomRom-Application/src/main/java/com/romrom/application/dto/AdminResponse.java
git commit -m "채팅방_완전_삭제_시_PostgreSQL_MongoDB_간_비원자_처리로_데이터_불일치_가능 : feat : #750 관리자 채팅방 관리 서비스(목록/상세/추출/즉시삭제) 추가 https://github.com/TEAM-ROMROM/RomRom-BE/issues/750"
```

---

## Task 7: AdminApiController 엔드포인트 4종 + Docs ApiChangeLog

**Files:**
- Modify: `RomRom-Web/src/main/java/com/romrom/web/controller/api/AdminApiController.java`
- Create: `RomRom-Application/src/test/java/com/romrom/application/service/AdminChatRoomServiceTest.java`

- [ ] **Step 1: 컨트롤러에 의존성 + 엔드포인트 추가**

`AdminApiController`에 필드 추가:
```java
    private final AdminChatRoomService adminChatRoomService;
```
import 추가:
```java
import com.romrom.application.service.AdminChatRoomService;
import org.springframework.http.HttpHeaders;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
```
엔드포인트 추가 (기존 메서드들 아래, Admin 컨벤션: POST + multipart + @ModelAttribute):
```java
    // soft-delete된(청소 대기) 채팅방 목록
    @PostMapping(value = "/chat-rooms/deleted-list", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AdminResponse> getDeletedChatRooms(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminChatRoomService.getDeletedChatRooms(request));
    }

    // 채팅방 상세 (메시지 전체)
    @PostMapping(value = "/chat-rooms/detail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AdminResponse> getChatRoomDetail(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminChatRoomService.getChatRoomDetail(request));
    }

    // 채팅방 추출 (.json.gz 다운로드)
    @PostMapping(value = "/chat-rooms/export", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Resource> exportChatRoom(@ModelAttribute AdminRequest request) {
        byte[] gzipBytes = adminChatRoomService.exportChatRoom(request);
        String fileName = "chat-room_" + request.getChatRoomId() + ".json.gz";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(gzipBytes.length)
            .body(new ByteArrayResource(gzipBytes));
    }

    // 채팅방 즉시 삭제 (아카이브 후 물리삭제)
    @PostMapping(value = "/chat-rooms/force-delete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> forceDeleteChatRoom(@ModelAttribute AdminRequest request) {
        adminChatRoomService.forceDeleteChatRoom(request);
        return ResponseEntity.ok().build();
    }
```

- [ ] **Step 2: Swagger Docs `@ApiChangeLog` 추가**

`AdminApiController`가 `@ApiChangeLogs`를 클래스/메서드에 다는 패턴이면(기존 메서드 확인), 새 엔드포인트에 `@Operation` + `@ApiChangeLogs` 최상단 항목 추가:
```java
    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.06.05", author = Author.SUHSAECHAN, issueNumber = 750,
            description = "채팅방 즉시 물리삭제를 soft delete + 배치 아카이브로 전환, 관리자 채팅방 관리 API 4종 추가")
    })
    @Operation(summary = "soft-delete 채팅방 목록", description = "deletedAt 표시된(청소 대기) 채팅방을 페이지네이션 조회")
```
(`Author` enum의 실제 작성자 값 확인 후 사용. 기존 엔드포인트의 `@ApiChangeLog` 작성 형식 그대로 따름.)

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :RomRom-Web:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 통합 테스트 작성**

`AdminChatRoomServiceTest.java`:
```java
package com.romrom.application.service;

import static me.suhsaechan.suhlogger.util.SuhLogger.lineLog;
import static me.suhsaechan.suhlogger.util.SuhLogger.timeLog;

import com.romrom.application.dto.AdminRequest;
import com.romrom.common.exception.CustomException;
import com.romrom.web.RomBackApplication;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = RomBackApplication.class)
@ActiveProfiles("dev")
@Slf4j
class AdminChatRoomServiceTest {

  @Autowired AdminChatRoomService adminChatRoomService;

  @Test
  public void mainTest() {
    lineLog("테스트시작");
    lineLog(null);
    timeLog(this::getChatRoomDetail_존재하지않는채팅방_CHAT_ROOM_NOT_FOUND반환_테스트);
    lineLog(null);
    timeLog(this::getDeletedChatRooms_빈목록도_정상응답_테스트);
    lineLog(null);
    lineLog("테스트종료");
  }

  private void getChatRoomDetail_존재하지않는채팅방_CHAT_ROOM_NOT_FOUND반환_테스트() {
    AdminRequest request = AdminRequest.builder().chatRoomId(UUID.randomUUID()).build();
    Assertions.assertThrows(CustomException.class,
        () -> adminChatRoomService.getChatRoomDetail(request));
  }

  private void getDeletedChatRooms_빈목록도_정상응답_테스트() {
    AdminRequest request = AdminRequest.builder().pageNumber(0).pageSize(20).build();
    Assertions.assertNotNull(adminChatRoomService.getDeletedChatRooms(request).getDeletedChatRooms());
  }
}
```

- [ ] **Step 5: 테스트 실행**

Run: `./gradlew :RomRom-Application:test --tests "com.romrom.application.service.AdminChatRoomServiceTest"`
Expected: PASS (두 시나리오 통과)

- [ ] **Step 6: Commit**

```bash
git add RomRom-Web/src/main/java/com/romrom/web/controller/api/AdminApiController.java \
        RomRom-Application/src/test/java/com/romrom/application/service/AdminChatRoomServiceTest.java
git commit -m "채팅방_완전_삭제_시_PostgreSQL_MongoDB_간_비원자_처리로_데이터_불일치_가능 : feat : #750 관리자 채팅방 관리 API 엔드포인트 4종 및 Docs 추가 https://github.com/TEAM-ROMROM/RomRom-BE/issues/750"
```

---

## Task 8: ChatControllerDocs 동작 변경 반영 + 전체 빌드 검증

**Files:**
- Modify: `RomRom-Domain-Chat`의 채팅 Docs 인터페이스 (존재 시 — `*ControllerDocs.java` 탐색)

- [ ] **Step 1: 채팅방 삭제/나가기 Docs description 갱신**

채팅방 나가기 엔드포인트의 Docs 인터페이스를 찾아(`grep -rn "ControllerDocs" RomRom-Domain-Chat RomRom-Web`), 나가기 동작 description에 "양쪽 모두 나간 경우 즉시 물리삭제 → soft delete 표시로 변경(실제 삭제는 30일 후 배치)" 반영 + `@ApiChangeLog` 최상단 항목 추가 (date `2026.06.05`, issueNumber `750`).
Docs 인터페이스가 없으면 이 스텝은 스킵하고 로그로 남긴다.

- [ ] **Step 2: 전체 빌드 + 전체 테스트**

Run: `./gradlew build -x test` 후 `./gradlew :RomRom-Application:test`
Expected: BUILD SUCCESSFUL, 신규 테스트 모두 PASS

- [ ] **Step 3: Flyway 마이그레이션 적용 확인 (dev 프로파일 기동)**

Run: `./gradlew :RomRom-Web:bootRun --args='--spring.profiles.active=dev'` (수동 기동 또는 기존 기동 절차)
Expected: `V1_4_64` 마이그레이션 적용 로그, `chat_room.deleted_at` 컬럼 생성 확인. 확인 후 종료.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "채팅방_완전_삭제_시_PostgreSQL_MongoDB_간_비원자_처리로_데이터_불일치_가능 : docs : #750 채팅방 삭제 동작 변경 Docs 반영 및 전체 빌드 검증 https://github.com/TEAM-ROMROM/RomRom-BE/issues/750"
```

---

## Self-Review 결과

**Spec coverage 확인:**
- §3 스키마 변경 → Task 1 ✅
- §4(a) 물리삭제→soft delete → Task 3 ✅
- §4(b) 목록 쿼리 필터 → Task 2 ✅
- §4(c) buildChatRoomDetailResponse → Task 3 Step 4 ✅
- §5 관리자 API 4종 → Task 6(서비스) + Task 7(엔드포인트) ✅
- §6 배치+아카이브 → Task 4(아카이브) + Task 5(스케줄러) ✅
- §6 물리삭제 순서(Mongo→PG) → Task 3 Step 3 `physicalDelete` ✅
- §7 에러처리/멱등 → Task 3(softDelete 멱등), Task 5(방별 try-catch), Task 6(아카이브 실패시 삭제중단) ✅
- §8 테스트 → Task 4·7 ✅
- §9 Swagger Docs → Task 7 Step 2, Task 8 Step 1 ✅

**Type consistency 확인:**
- `softDelete()` — Task 1 정의, Task 3 사용 ✅
- `physicalDelete(UUID)` — Task 3 정의, Task 5·6 호출 ✅
- `findCleanupTargets(LocalDateTime)` — Task 2 정의, Task 5 호출 ✅
- `findByDeletedAtIsNotNull(Pageable)` — Task 2 정의, Task 6 호출 ✅
- `archiveToFile(ChatRoom)` / `archiveToGzipBytes(ChatRoom)` — Task 4 정의, Task 5·6 호출 ✅
- `findAllByChatRoomId(UUID)` — Task 4 Step 3 조건부 추가, Task 6 사용 (확인 플래그 명시) ✅

**확인 완료 항목(구현 시 반영):**
- ErrorCode: `CHAT_ROOM_NOT_FOUND` ❌ → **이미 있는 `CHATROOM_NOT_FOUND`(언더스코어 없음) 사용** (ErrorCode.java:177). `CHAT_ROOM_EXPORT_FAILED`는 없으므로 신규 추가 (형식: `CHATROOM_EXPORT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "채팅방 추출에 실패했습니다."),`)
- `ChatMessage.getCreatedDate()` → **`LocalDateTime` 확정** (BaseMongoEntity)
- `Pageable.unpaged()` Slice 전체 조회 가능 여부 (불가 시 `findAllByChatRoomId` 사용 — Task 4 Step 3)
- `Author.SUHSAECHAN = "서새찬"` (String 상수, enum 아님). `@ApiChangeLog(author = Author.SUHSAECHAN, ...)`
- 채팅 Docs: `ChatControllerDocs.java`, `ChatWebSocketControllerDocs.java` **둘 다 존재** (RomRom-Web/.../controller/api/)
