package com.romrom.application.service;

import static me.suhsaechan.suhlogger.util.SuhLogger.lineLog;
import static me.suhsaechan.suhlogger.util.SuhLogger.timeLog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.romrom.application.dto.ChatRoomArchiveDto;
import com.romrom.web.RomBackApplication;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = RomBackApplication.class)
@ActiveProfiles("dev")
@Slf4j
class ChatRoomArchiveServiceTest {

  // 서비스가 주입받는 것과 동일한 Primary ObjectMapper (JavaTimeModule 포함, LocalDateTime 직렬화 검증용)
  @Autowired
  ObjectMapper objectMapper;

  @Test
  public void mainTest() {
    lineLog("테스트시작");

    lineLog(null);
    timeLog(this::gzip_압축_해제_라운드트립_JSON_복원_테스트);
    lineLog(null);

    lineLog("테스트종료");
  }

  // DTO → JSON → gzip 압축 → gzip 해제 → JSON 역직렬화 라운드트립 검증 (DB 미접근)
  public void gzip_압축_해제_라운드트립_JSON_복원_테스트() throws Exception {
    UUID chatRoomId = UUID.randomUUID();

    ChatRoomArchiveDto.MessageEntry messageEntry = ChatRoomArchiveDto.MessageEntry.builder()
        .chatMessageId("msg-1")
        .senderId(UUID.randomUUID())
        .recipientId(UUID.randomUUID())
        .content("안녕하세요 테스트 메시지")
        .imageUrls(List.of("https://example.com/image1.jpg"))
        .type("NORMAL")
        .createdDate(LocalDateTime.now())
        .build();

    ChatRoomArchiveDto originalArchiveDto = ChatRoomArchiveDto.builder()
        .chatRoomId(chatRoomId)
        .tradeReceiverId(UUID.randomUUID())
        .tradeSenderId(UUID.randomUUID())
        .deletedAt(LocalDateTime.now())
        .archivedAt(LocalDateTime.now())   // LocalDateTime 직렬화 검증
        .messages(List.of(messageEntry))
        .build();

    // 직렬화 → gzip 압축
    byte[] archiveJsonBytes = objectMapper.writeValueAsBytes(originalArchiveDto);
    ByteArrayOutputStream gzipByteBuffer = new ByteArrayOutputStream();
    try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(gzipByteBuffer)) {
      gzipOutputStream.write(archiveJsonBytes);
    }
    byte[] compressedBytes = gzipByteBuffer.toByteArray();
    Assertions.assertTrue(compressedBytes.length > 0, "gzip 압축 결과가 비어있으면 안 됨");

    // gzip 해제 → 역직렬화
    byte[] decompressedBytes;
    try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(compressedBytes))) {
      decompressedBytes = gzipInputStream.readAllBytes();
    }
    ChatRoomArchiveDto restoredArchiveDto = objectMapper.readValue(decompressedBytes, ChatRoomArchiveDto.class);

    // 검증
    Assertions.assertEquals(chatRoomId, restoredArchiveDto.getChatRoomId(), "chatRoomId 라운드트립 일치해야 함");
    Assertions.assertEquals(1, restoredArchiveDto.getMessages().size(), "메시지 개수 일치해야 함");
    Assertions.assertEquals("msg-1", restoredArchiveDto.getMessages().get(0).getChatMessageId(),
        "메시지 chatMessageId 일치해야 함");
    Assertions.assertEquals(originalArchiveDto.getArchivedAt(), restoredArchiveDto.getArchivedAt(),
        "LocalDateTime(archivedAt) 직렬화/역직렬화 일치해야 함");

    lineLog("gzip 라운드트립 + LocalDateTime 직렬화 검증 완료");
  }
}
