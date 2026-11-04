package com.romrom.chat.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.chat.dto.ChatMessageRequest;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.mongo.ChatUserState;
import com.romrom.chat.entity.mongo.MessageType;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.chat.repository.mongo.ChatMessageRepository;
import com.romrom.chat.repository.mongo.ChatUserStateRepository;
import com.romrom.chat.repository.postgres.ChatRoomRepository;
import com.romrom.common.service.UgcFilterService;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.member.entity.Member;
import com.romrom.member.service.MemberBlockService;
import com.romrom.notification.event.ChatMessageReceivedEvent;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 방별 알림 끄기: 끈 상대에게는 채팅 푸시 이벤트 미발행, 켠 상대에게는 발행
 */
@ExtendWith(MockitoExtension.class)
class ChatMessageMutedPushTest {

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

  private final Member senderMember = Member.builder().memberId(UUID.randomUUID()).nickname("보낸이").build();
  private final Member recipientMember = Member.builder().memberId(UUID.randomUUID()).build();
  private ChatRoom chatRoom;

  @BeforeEach
  void setUp() {
    TransactionSynchronizationManager.initSynchronization();
    TradeRequestHistory tradeRequestHistory = TradeRequestHistory.builder()
        .giveItem(Item.builder().build())
        .takeItem(Item.builder().build())
        .build();
    chatRoom = ChatRoom.builder()
        .chatRoomId(UUID.randomUUID())
        .tradeSender(senderMember)
        .tradeReceiver(recipientMember)
        .tradeRequestHistory(tradeRequestHistory)
        .build();
    when(chatRoomRepository.findByChatRoomIdWithSenderAndReceiver(chatRoom.getChatRoomId()))
        .thenReturn(Optional.of(chatRoom));
    when(chatUserStateRepository.findByChatRoomIdAndMemberId(chatRoom.getChatRoomId(), senderMember.getMemberId()))
        .thenReturn(Optional.of(ChatUserState.create(chatRoom.getChatRoomId(), senderMember.getMemberId())));
    when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @AfterEach
  void tearDown() {
    TransactionSynchronizationManager.clearSynchronization();
  }

  private void sendTextAndCommit(ChatUserState recipientState) {
    when(chatUserStateRepository.findByChatRoomIdAndMemberId(chatRoom.getChatRoomId(), recipientMember.getMemberId()))
        .thenReturn(Optional.of(recipientState));
    ChatMessageRequest request = ChatMessageRequest.builder()
        .chatRoomId(chatRoom.getChatRoomId()).content("안녕하세요").type(MessageType.TEXT).build();

    chatMessageService.saveAndSendMessage(request, new CustomUserDetails(senderMember));
    TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
  }

  @Test
  @DisplayName("방 밖 + 알림 끈 상대 > 채팅 푸시 이벤트 없음")
  void 알림_끈_방_푸시_생략() {
    ChatUserState mutedRecipientState = ChatUserState.builder()
        .chatRoomId(chatRoom.getChatRoomId()).memberId(recipientMember.getMemberId())
        .leftAt(LocalDateTime.now()).notificationMutedAt(LocalDateTime.now()).build();

    sendTextAndCommit(mutedRecipientState);

    verify(eventPublisher, never()).publishEvent(any(ChatMessageReceivedEvent.class));
  }

  @Test
  @DisplayName("방 밖 + 알림 켠 상대 > 채팅 푸시 이벤트 발행")
  void 알림_켠_방_푸시_발행() {
    sendTextAndCommit(ChatUserState.create(chatRoom.getChatRoomId(), recipientMember.getMemberId()));

    verify(eventPublisher).publishEvent(any(ChatMessageReceivedEvent.class));
  }
}
