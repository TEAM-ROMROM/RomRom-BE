package com.romrom.chat.service;

import com.romrom.chat.dto.ChatRoomRequest;
import com.romrom.chat.dto.ChatRoomResponse;
import com.romrom.chat.entity.mongo.ChatUserState;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.chat.repository.mongo.ChatUserStateRepository;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채팅방별 알림 끄기: 내 방 상태에 끈 시각 저장 (끈 방은 채팅 푸시만 생략, 안 읽음 수 유지)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatRoomNotificationMuteService {

  private final ChatMessageService chatMessageService;
  private final ChatUserStateRepository chatUserStateRepository;

  /**
   * 알림 끄기·켜기: 요청 검증 > 멤버 확인 > 끈 시각 원자적 갱신 > 변경 후 값 반환
   */
  @Transactional
  public ChatRoomResponse updateNotificationMuted(ChatRoomRequest request) {
    if (request.getChatRoomId() == null || request.getIsNotificationMuted() == null) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    UUID memberId = request.getMember().getMemberId();
    ChatRoom chatRoom = chatMessageService.validateChatRoomMember(memberId, request.getChatRoomId());

    LocalDateTime notificationMutedAt = Boolean.TRUE.equals(request.getIsNotificationMuted()) ? LocalDateTime.now() : null;
    ChatUserState myState = chatUserStateRepository
        .updateNotificationMutedAt(chatRoom.getChatRoomId(), memberId, notificationMutedAt);
    if (myState == null) {
      throw new CustomException(ErrorCode.CHAT_USER_STATE_NOT_FOUND);
    }
    log.debug("[chat mute] updated : chatRoomId={}, isMuted={}", chatRoom.getChatRoomId(), myState.isNotificationMuted());

    return ChatRoomResponse.builder()
        .isNotificationMuted(myState.isNotificationMuted())
        .build();
  }
}
