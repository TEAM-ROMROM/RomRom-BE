package com.romrom.chat.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.chat.dto.ChatMessageRequest;
import com.romrom.chat.entity.mongo.ChatUserState;
import com.romrom.chat.entity.mongo.MessageType;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.chat.repository.mongo.ChatMessageRepository;
import com.romrom.chat.repository.mongo.ChatUserStateRepository;
import com.romrom.chat.repository.postgres.ChatRoomRepository;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.service.UgcFilterService;
import com.romrom.member.entity.Member;
import com.romrom.member.service.MemberBlockService;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 메시지 전송 차단 단위 테스트: 종료된 방(deletedAt), 본인이 나간 방(removedAt) > CANNOT_SEND_MESSAGE_TO_CLOSED_CHATROOM
 */
@ExtendWith(MockitoExtension.class)
class ChatMessageSendBlockTest {

  @Mock private ChatRoomRepository chatRoomRepository;
  @Mock private ChatMessageRepository chatMessageRepository;
  @Mock private ChatWebSocketService chatWebSocketService;
  @Mock private MemberBlockService memberBlockService;
  @Mock private ChatUserStateRepository chatUserStateRepository;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private UgcFilterService ugcFilterService;
  @Mock private ChatActionRecommendationService chatActionRecommendationService;

  @InjectMocks
  private ChatMessageService chatMessageService;

  private Member senderMember;
  private Member recipientMember;

  @BeforeEach
  void setUp() {
    senderMember = Member.builder().memberId(UUID.randomUUID()).build();
    recipientMember = Member.builder().memberId(UUID.randomUUID()).build();
  }

  private ChatRoom givenChatRoom(LocalDateTime deletedAt) {
    ChatRoom chatRoom = ChatRoom.builder()
        .chatRoomId(UUID.randomUUID())
        .tradeSender(senderMember)
        .tradeReceiver(recipientMember)
        .deletedAt(deletedAt)
        .build();
    when(chatRoomRepository.findByChatRoomIdWithSenderAndReceiver(chatRoom.getChatRoomId()))
        .thenReturn(Optional.of(chatRoom));
    return chatRoom;
  }

  private ChatMessageRequest createTextMessageRequest(ChatRoom chatRoom) {
    return ChatMessageRequest.builder()
        .chatRoomId(chatRoom.getChatRoomId())
        .content("안녕하세요")
        .type(MessageType.TEXT)
        .build();
  }

  @Test
  @DisplayName("종료된 방(deletedAt)으로 보내면 저장 없이 CANNOT_SEND_MESSAGE_TO_CLOSED_CHATROOM")
  void 종료된_방_전송_차단() {
    ChatRoom closedChatRoom = givenChatRoom(LocalDateTime.now());

    CustomException exception = Assertions.assertThrows(CustomException.class,
        () -> chatMessageService.saveAndSendMessage(
            createTextMessageRequest(closedChatRoom), new CustomUserDetails(senderMember)));

    Assertions.assertEquals(ErrorCode.CANNOT_SEND_MESSAGE_TO_CLOSED_CHATROOM, exception.getErrorCode());
    verify(chatMessageRepository, never()).save(any());
  }

  @Test
  @DisplayName("본인이 나간 방(강제 퇴장 포함)으로 보내면 저장 없이 CANNOT_SEND_MESSAGE_TO_CLOSED_CHATROOM")
  void 본인이_나간_방_전송_차단() {
    ChatRoom activeChatRoom = givenChatRoom(null);
    ChatUserState removedSenderState = ChatUserState.create(activeChatRoom.getChatRoomId(), senderMember.getMemberId());
    removedSenderState.removeRoom();
    when(chatUserStateRepository.findByChatRoomIdAndMemberId(activeChatRoom.getChatRoomId(), senderMember.getMemberId()))
        .thenReturn(Optional.of(removedSenderState));

    CustomException exception = Assertions.assertThrows(CustomException.class,
        () -> chatMessageService.saveAndSendMessage(
            createTextMessageRequest(activeChatRoom), new CustomUserDetails(senderMember)));

    Assertions.assertEquals(ErrorCode.CANNOT_SEND_MESSAGE_TO_CLOSED_CHATROOM, exception.getErrorCode());
    verify(chatMessageRepository, never()).save(any());
  }
}
