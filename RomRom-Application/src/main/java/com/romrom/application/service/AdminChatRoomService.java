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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자용 soft-delete 채팅방 관리 서비스 (#750).
 * 청소 대기(soft-delete) 채팅방의 목록/상세 조회, 백업 추출, 즉시 물리 삭제를 담당한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminChatRoomService {

  private final ChatRoomRepository chatRoomRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final ChatRoomArchiveService chatRoomArchiveService;
  private final ChatRoomService chatRoomService;

  /**
   * soft-delete된(청소 대기) 채팅방 목록을 deletedAt 기준 정렬하여 페이지 조회한다.
   */
  @Transactional(readOnly = true)
  public AdminResponse getDeletedChatRooms(AdminRequest request) {
    // 청소 대기 목록은 삭제 시각(deletedAt) 기준 정렬이 의미 있으므로 sortBy 대신 deletedAt 고정
    PageRequest pageRequest = PageRequest.of(
        request.getPageNumber(),
        request.getPageSize(),
        Sort.by(request.getSortDirection(), "deletedAt"));

    Page<ChatRoom> deletedChatRooms = chatRoomRepository.findByDeletedAtIsNotNull(pageRequest);

    return AdminResponse.builder()
        .deletedChatRooms(deletedChatRooms)
        .totalCount(deletedChatRooms.getTotalElements())
        .build();
  }

  /**
   * 채팅방 상세(엔티티 + 전체 메시지)를 조회한다.
   */
  @Transactional(readOnly = true)
  public AdminResponse getChatRoomDetail(AdminRequest request) {
    ChatRoom chatRoom = chatRoomRepository.findById(request.getChatRoomId())
        .orElseThrow(() -> new CustomException(ErrorCode.CHATROOM_NOT_FOUND));

    List<ChatMessage> chatMessages =
        chatMessageRepository.findByChatRoomIdOrderByCreatedDateAsc(request.getChatRoomId());

    return AdminResponse.builder()
        .chatRoom(chatRoom)
        .chatMessages(chatMessages)
        .build();
  }

  /**
   * 채팅방을 JSON → gzip으로 압축한 byte[]로 추출한다 (관리자 다운로드용).
   */
  @Transactional(readOnly = true)
  public byte[] exportChatRoom(AdminRequest request) {
    ChatRoom chatRoom = chatRoomRepository.findById(request.getChatRoomId())
        .orElseThrow(() -> new CustomException(ErrorCode.CHATROOM_NOT_FOUND));

    try {
      return chatRoomArchiveService.archiveToGzipBytes(chatRoom);
    } catch (IOException e) {
      log.error("채팅방 추출 실패: chatRoomId={}", request.getChatRoomId(), e);
      throw new CustomException(ErrorCode.CHATROOM_EXPORT_FAILED);
    }
  }

  /**
   * 채팅방을 즉시 물리 삭제한다.
   * 삭제 전 반드시 파일 백업을 수행하며, 백업 실패 시 데이터 유실 방지를 위해 삭제를 중단한다.
   */
  @Transactional
  public void forceDeleteChatRoom(AdminRequest request) {
    ChatRoom chatRoom = chatRoomRepository.findById(request.getChatRoomId())
        .orElseThrow(() -> new CustomException(ErrorCode.CHATROOM_NOT_FOUND));

    try {
      chatRoomArchiveService.archiveToFile(chatRoom);
    } catch (IOException e) {
      // 백업이 안 됐으면 영구 삭제 시 복구 불가 → 삭제 중단
      log.error("채팅방 백업 실패로 즉시 삭제 중단: chatRoomId={}", request.getChatRoomId(), e);
      throw new CustomException(ErrorCode.CHATROOM_EXPORT_FAILED);
    }

    chatRoomService.physicalDelete(request.getChatRoomId());
    log.info("채팅방 즉시 삭제 완료: chatRoomId={}", request.getChatRoomId());
  }
}
