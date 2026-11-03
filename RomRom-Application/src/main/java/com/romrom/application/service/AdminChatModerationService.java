package com.romrom.application.service;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.chat.repository.mongo.ChatMessageRepository;
import com.romrom.chat.repository.postgres.ChatRoomRepository;
import com.romrom.chat.service.ChatRoomService;
import com.romrom.common.constant.SanctionType;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.member.entity.mongo.SanctionHistory;
import com.romrom.member.repository.mongo.SanctionHistoryRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 채팅 운영 조치: 메시지 페이지 조회, 메시지 숨김·해제, 채팅방 강제 종료, 회원 강제 퇴장
 * 조치마다 대상 회원 SanctionHistory에 감사 기록 (조회 제외)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminChatModerationService {

  private final ChatRoomRepository chatRoomRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final ChatRoomService chatRoomService;
  private final SanctionHistoryRepository sanctionHistoryRepository;

  /**
   * 채팅방 메시지 페이지 (최신순, 숨김 메시지도 원본 그대로)
   */
  @Transactional(readOnly = true)
  public AdminResponse getChatMessages(AdminRequest request) {
    findChatRoom(request.getChatRoomId());
    PageRequest pageRequest = PageRequest.of(
        request.getPageNumber(), request.getPageSize(), Sort.by(Sort.Direction.DESC, "createdDate"));
    Slice<ChatMessage> chatMessageSlice =
        chatMessageRepository.findByChatRoomIdOrderByCreatedDateDesc(request.getChatRoomId(), pageRequest);
    return AdminResponse.builder()
        .chatMessageSlice(chatMessageSlice)
        .build();
  }

  /**
   * 메시지 숨김: hiddenAt 기록 (원본 보존, 회원 조회 시 마스킹) > 발신자 감사 기록
   */
  @Transactional
  public AdminResponse hideChatMessage(AdminRequest request) {
    ChatMessage chatMessage = findChatMessage(request.getChatMessageId());
    if (!chatMessage.isHidden()) {
      chatMessage.hideByAdmin(request.getExecutorAdminId());
      chatMessageRepository.save(chatMessage);
      recordChatAuditIfMember(chatMessage.getSenderId(), SanctionType.CHAT_MESSAGE_HIDE,
          describeChatMessageTarget(chatMessage), request);
      log.info("[admin chat] message hidden : chatMessageId={}, executorAdminId={}",
          chatMessage.getChatMessageId(), request.getExecutorAdminId());
    }
    return AdminResponse.builder().build();
  }

  /**
   * 메시지 숨김 해제 > 발신자 감사 기록
   */
  @Transactional
  public AdminResponse unhideChatMessage(AdminRequest request) {
    ChatMessage chatMessage = findChatMessage(request.getChatMessageId());
    if (chatMessage.isHidden()) {
      chatMessage.unhide();
      chatMessageRepository.save(chatMessage);
      recordChatAuditIfMember(chatMessage.getSenderId(), SanctionType.CHAT_MESSAGE_UNHIDE,
          describeChatMessageTarget(chatMessage), request);
      log.info("[admin chat] message unhidden : chatMessageId={}, executorAdminId={}",
          chatMessage.getChatMessageId(), request.getExecutorAdminId());
    }
    return AdminResponse.builder().build();
  }

  /**
   * 채팅방 강제 종료: 진행 중 거래 취소 > soft delete > 참여자 2명 감사 기록
   */
  @Transactional
  public AdminResponse forceCloseChatRoom(AdminRequest request) {
    ChatRoom chatRoom = findChatRoom(request.getChatRoomId());
    chatRoomService.adminForceCloseChatRoom(chatRoom.getChatRoomId());

    String chatRoomTarget = "chatRoomId=" + chatRoom.getChatRoomId();
    for (UUID participantMemberId : List.of(
        chatRoom.getTradeSender().getMemberId(), chatRoom.getTradeReceiver().getMemberId())) {
      recordChatAuditIfMember(participantMemberId, SanctionType.CHAT_ROOM_FORCE_CLOSE, chatRoomTarget, request);
    }
    return AdminResponse.builder().build();
  }

  /**
   * 회원 강제 퇴장: 회원 직접 나가기와 같은 처리 > 대상 회원 감사 기록
   */
  @Transactional
  public AdminResponse forceLeaveChatRoom(AdminRequest request) {
    if (request.getMemberId() == null) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    ChatRoom chatRoom = findChatRoom(request.getChatRoomId());
    chatRoomService.adminForceLeaveChatRoom(chatRoom.getChatRoomId(), request.getMemberId());
    recordChatAuditIfMember(request.getMemberId(), SanctionType.CHAT_ROOM_FORCE_LEAVE,
        "chatRoomId=" + chatRoom.getChatRoomId(), request);
    return AdminResponse.builder().build();
  }

  private ChatRoom findChatRoom(UUID chatRoomId) {
    if (chatRoomId == null) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    return chatRoomRepository.findById(chatRoomId)
        .orElseThrow(() -> new CustomException(ErrorCode.CHATROOM_NOT_FOUND));
  }

  private ChatMessage findChatMessage(String chatMessageId) {
    if (chatMessageId == null || chatMessageId.isBlank()) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    return chatMessageRepository.findById(chatMessageId)
        .orElseThrow(() -> new CustomException(ErrorCode.CHAT_MESSAGE_NOT_FOUND));
  }

  private static String describeChatMessageTarget(ChatMessage chatMessage) {
    return "chatRoomId=" + chatMessage.getChatRoomId() + ", chatMessageId=" + chatMessage.getChatMessageId();
  }

  /**
   * 감사 기록: [조치] 대상 · 사유 (시스템 메시지처럼 발신자 없는 경우 기록 생략)
   */
  private void recordChatAuditIfMember(UUID targetMemberId, SanctionType sanctionType, String actionTarget,
      AdminRequest request) {
    if (targetMemberId == null) {
      return;
    }
    String actionReason = request.getChatAdminActionReason();
    String auditReason = "[" + sanctionType.name() + "] " + actionTarget
        + (actionReason != null && !actionReason.isBlank() ? " / " + actionReason.trim() : "");
    sanctionHistoryRepository.save(SanctionHistory.builder()
        .memberId(targetMemberId)
        .suspendReason(auditReason)
        .suspendedAt(LocalDateTime.now(ZoneOffset.UTC))
        .sanctionType(sanctionType)
        .executorAdminId(request.getExecutorAdminId())
        .build());
  }
}
