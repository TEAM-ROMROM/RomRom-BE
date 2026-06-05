package com.romrom.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.romrom.application.dto.ChatRoomArchiveDto;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.chat.repository.mongo.ChatMessageRepository;
import java.io.ByteArrayOutputStream;
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

/**
 * 채팅방 데이터를 JSON으로 조립 → gzip 압축하여 보관하는 서비스 (#750).
 * 관리자 export(다운로드)와 배치 청소가 공용으로 사용한다.
 * 이미지는 URL 참조(imageUrls)만 담고 바이너리는 백업하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatRoomArchiveService {

  private final ChatMessageRepository chatMessageRepository;
  // JavaTimeModule이 등록된 Primary ObjectMapper 주입 (LocalDateTime 직렬화 지원, JacksonConfig 참조)
  private final ObjectMapper objectMapper;

  // 호스트 마운트 백업 경로 (미설정 시 기본값)
  @Value("${chat.archive.backup-dir:./backup/chat-rooms}")
  private String backupDir;

  // 백업 파일명 타임스탬프 포맷
  private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

  /**
   * 채팅방을 JSON → gzip으로 압축해 호스트 마운트 backup 폴더에 .json.gz로 저장한다 (배치 청소용).
   *
   * @return 저장된 파일 경로
   */
  public Path archiveToFile(ChatRoom room) throws IOException {
    ChatRoomArchiveDto archiveDto = buildArchiveDto(room);
    byte[] archiveJsonBytes = objectMapper.writeValueAsBytes(archiveDto);

    Path backupDirPath = Paths.get(backupDir);
    Files.createDirectories(backupDirPath);

    // 파일명 타임스탬프는 삭제 시각 우선, 없으면 현재 시각
    LocalDateTime fileTimestampSource = room.getDeletedAt() != null ? room.getDeletedAt() : LocalDateTime.now();
    String archiveFileName = room.getChatRoomId() + "_" + fileTimestampSource.format(FILE_TIMESTAMP) + ".json.gz";
    Path archiveFilePath = backupDirPath.resolve(archiveFileName);

    try (OutputStream fileOutputStream = Files.newOutputStream(archiveFilePath);
        GZIPOutputStream gzipOutputStream = new GZIPOutputStream(fileOutputStream)) {
      gzipOutputStream.write(archiveJsonBytes);
    }

    log.info("채팅방 아카이브 저장 완료: chatRoomId={}, path={}", room.getChatRoomId(), archiveFilePath);
    return archiveFilePath;
  }

  /**
   * 채팅방을 JSON → gzip으로 압축한 byte[]를 반환한다 (관리자 다운로드용, 파일 미생성).
   */
  public byte[] archiveToGzipBytes(ChatRoom room) throws IOException {
    ChatRoomArchiveDto archiveDto = buildArchiveDto(room);
    byte[] archiveJsonBytes = objectMapper.writeValueAsBytes(archiveDto);

    ByteArrayOutputStream gzipByteBuffer = new ByteArrayOutputStream();
    try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(gzipByteBuffer)) {
      gzipOutputStream.write(archiveJsonBytes);
    }
    return gzipByteBuffer.toByteArray();
  }

  // 채팅방 + 전체 메시지를 아카이브 DTO로 조립 (이미지는 URL 참조만)
  private ChatRoomArchiveDto buildArchiveDto(ChatRoom room) {
    UUID chatRoomId = room.getChatRoomId();
    List<ChatMessage> chatMessages = chatMessageRepository.findByChatRoomIdOrderByCreatedDateAsc(chatRoomId);

    List<ChatRoomArchiveDto.MessageEntry> messageEntries = chatMessages.stream()
        .map(chatMessage -> ChatRoomArchiveDto.MessageEntry.builder()
            .chatMessageId(chatMessage.getChatMessageId())
            .senderId(chatMessage.getSenderId())
            .recipientId(chatMessage.getRecipientId())
            .content(chatMessage.getContent())
            .imageUrls(chatMessage.getImageUrls())
            // type null 방어 (enum이면 name() 문자열화)
            .type(chatMessage.getType() != null ? chatMessage.getType().name() : null)
            .createdDate(chatMessage.getCreatedDate())
            .build())
        .collect(Collectors.toList());

    return ChatRoomArchiveDto.builder()
        .chatRoomId(chatRoomId)
        .tradeReceiverId(room.getTradeReceiver().getMemberId())
        .tradeSenderId(room.getTradeSender().getMemberId())
        .deletedAt(room.getDeletedAt())
        .archivedAt(LocalDateTime.now())
        .messages(messageEntries)
        .build();
  }
}
