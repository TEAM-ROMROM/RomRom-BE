package com.romrom.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.romrom.application.dto.AdminRequest;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.mongo.MessageType;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.chat.repository.mongo.ChatMessageRepository;
import com.romrom.chat.repository.postgres.ChatRoomRepository;
import com.romrom.chat.service.ChatRoomService;
import com.romrom.common.constant.SanctionType;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.member.entity.Member;
import com.romrom.member.entity.mongo.SanctionHistory;
import com.romrom.member.repository.mongo.SanctionHistoryRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 관리자 채팅 운영 조치 단위 테스트: 숨김 저장·감사 기록, 시스템 메시지 감사 생략, 강제 종료 2명 기록, 강제 퇴장 검증
 */
@ExtendWith(MockitoExtension.class)
class AdminChatModerationServiceTest {

  private static final UUID EXECUTOR_ADMIN_ID = UUID.randomUUID();

  @Mock private ChatRoomRepository chatRoomRepository;
  @Mock private ChatMessageRepository chatMessageRepository;
  @Mock private ChatRoomService chatRoomService;
  @Mock private SanctionHistoryRepository sanctionHistoryRepository;

  @InjectMocks
  private AdminChatModerationService adminChatModerationService;

  private static ChatMessage createMessage(UUID senderId) {
    return ChatMessage.builder()
        .chatMessageId("message-1")
        .chatRoomId(UUID.randomUUID())
        .senderId(senderId)
        .content("내용")
        .type(senderId == null ? MessageType.SYSTEM : MessageType.TEXT)
        .build();
  }

  private static ChatRoom createChatRoom(UUID tradeSenderId, UUID tradeReceiverId) {
    return ChatRoom.builder()
        .chatRoomId(UUID.randomUUID())
        .tradeSender(Member.builder().memberId(tradeSenderId).build())
        .tradeReceiver(Member.builder().memberId(tradeReceiverId).build())
        .build();
  }

  private static AdminRequest createRequest() {
    AdminRequest adminRequest = new AdminRequest();
    adminRequest.setExecutorAdminId(EXECUTOR_ADMIN_ID);
    adminRequest.setChatAdminActionReason("욕설");
    return adminRequest;
  }

  @Test
  @DisplayName("메시지 숨김: hiddenAt 저장 + 발신자 CHAT_MESSAGE_HIDE 감사 기록 (사유 포함)")
  void 메시지_숨김_저장과_감사기록() {
    UUID senderId = UUID.randomUUID();
    ChatMessage chatMessage = createMessage(senderId);
    when(chatMessageRepository.findById("message-1")).thenReturn(Optional.of(chatMessage));
    AdminRequest adminRequest = createRequest();
    adminRequest.setChatMessageId("message-1");

    adminChatModerationService.hideChatMessage(adminRequest);

    Assertions.assertTrue(chatMessage.isHidden());
    Assertions.assertEquals(EXECUTOR_ADMIN_ID, chatMessage.getHiddenByAdminId());
    verify(chatMessageRepository).save(chatMessage);
    ArgumentCaptor<SanctionHistory> auditCaptor = ArgumentCaptor.forClass(SanctionHistory.class);
    verify(sanctionHistoryRepository).save(auditCaptor.capture());
    Assertions.assertEquals(senderId, auditCaptor.getValue().getMemberId());
    Assertions.assertEquals(SanctionType.CHAT_MESSAGE_HIDE, auditCaptor.getValue().getSanctionType());
    Assertions.assertEquals(EXECUTOR_ADMIN_ID, auditCaptor.getValue().getExecutorAdminId());
    Assertions.assertTrue(auditCaptor.getValue().getSuspendReason().contains("욕설"));
  }

  @Test
  @DisplayName("이미 숨긴 메시지를 다시 숨기면 저장·감사 기록 없음")
  void 이미_숨긴_메시지_재숨김_무시() {
    ChatMessage chatMessage = createMessage(UUID.randomUUID());
    chatMessage.hideByAdmin(UUID.randomUUID());
    when(chatMessageRepository.findById("message-1")).thenReturn(Optional.of(chatMessage));
    AdminRequest adminRequest = createRequest();
    adminRequest.setChatMessageId("message-1");

    adminChatModerationService.hideChatMessage(adminRequest);

    verify(chatMessageRepository, never()).save(any());
    verify(sanctionHistoryRepository, never()).save(any());
  }

  @Test
  @DisplayName("발신자 없는 시스템 메시지 숨김은 감사 기록 생략")
  void 시스템_메시지_숨김_감사기록_생략() {
    ChatMessage systemMessage = createMessage(null);
    when(chatMessageRepository.findById("message-1")).thenReturn(Optional.of(systemMessage));
    AdminRequest adminRequest = createRequest();
    adminRequest.setChatMessageId("message-1");

    adminChatModerationService.hideChatMessage(adminRequest);

    verify(chatMessageRepository).save(systemMessage);
    verify(sanctionHistoryRepository, never()).save(any());
  }

  @Test
  @DisplayName("없는 메시지 숨김은 CHAT_MESSAGE_NOT_FOUND")
  void 없는_메시지_숨김_404() {
    when(chatMessageRepository.findById("missing")).thenReturn(Optional.empty());
    AdminRequest adminRequest = createRequest();
    adminRequest.setChatMessageId("missing");

    CustomException exception = Assertions.assertThrows(CustomException.class,
        () -> adminChatModerationService.hideChatMessage(adminRequest));
    Assertions.assertEquals(ErrorCode.CHAT_MESSAGE_NOT_FOUND, exception.getErrorCode());
  }

  @Test
  @DisplayName("강제 종료: 채팅방 종료 위임 + 참여자 2명 CHAT_ROOM_FORCE_CLOSE 기록")
  void 강제_종료_참여자_2명_기록() {
    ChatRoom chatRoom = createChatRoom(UUID.randomUUID(), UUID.randomUUID());
    when(chatRoomRepository.findById(chatRoom.getChatRoomId())).thenReturn(Optional.of(chatRoom));
    AdminRequest adminRequest = createRequest();
    adminRequest.setChatRoomId(chatRoom.getChatRoomId());

    adminChatModerationService.forceCloseChatRoom(adminRequest);

    verify(chatRoomService).adminForceCloseChatRoom(chatRoom.getChatRoomId());
    ArgumentCaptor<SanctionHistory> auditCaptor = ArgumentCaptor.forClass(SanctionHistory.class);
    verify(sanctionHistoryRepository, times(2)).save(auditCaptor.capture());
    List<UUID> auditedMemberIds = auditCaptor.getAllValues().stream().map(SanctionHistory::getMemberId).toList();
    Assertions.assertTrue(auditedMemberIds.contains(chatRoom.getTradeSender().getMemberId()));
    Assertions.assertTrue(auditedMemberIds.contains(chatRoom.getTradeReceiver().getMemberId()));
  }

  @Test
  @DisplayName("강제 퇴장: memberId 누락이면 INVALID_REQUEST, 처리 위임 없음")
  void 강제_퇴장_memberId_누락_400() {
    AdminRequest adminRequest = createRequest();
    adminRequest.setChatRoomId(UUID.randomUUID());

    CustomException exception = Assertions.assertThrows(CustomException.class,
        () -> adminChatModerationService.forceLeaveChatRoom(adminRequest));
    Assertions.assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());
    verify(chatRoomService, never()).adminForceLeaveChatRoom(any(), any());
  }

  @Test
  @DisplayName("강제 퇴장: 처리 위임 + 대상 회원 CHAT_ROOM_FORCE_LEAVE 기록")
  void 강제_퇴장_위임과_감사기록() {
    UUID targetMemberId = UUID.randomUUID();
    ChatRoom chatRoom = createChatRoom(targetMemberId, UUID.randomUUID());
    when(chatRoomRepository.findById(chatRoom.getChatRoomId())).thenReturn(Optional.of(chatRoom));
    AdminRequest adminRequest = createRequest();
    adminRequest.setChatRoomId(chatRoom.getChatRoomId());
    adminRequest.setMemberId(targetMemberId);

    adminChatModerationService.forceLeaveChatRoom(adminRequest);

    verify(chatRoomService).adminForceLeaveChatRoom(chatRoom.getChatRoomId(), targetMemberId);
    ArgumentCaptor<SanctionHistory> auditCaptor = ArgumentCaptor.forClass(SanctionHistory.class);
    verify(sanctionHistoryRepository).save(auditCaptor.capture());
    Assertions.assertEquals(targetMemberId, auditCaptor.getValue().getMemberId());
    Assertions.assertEquals(SanctionType.CHAT_ROOM_FORCE_LEAVE, auditCaptor.getValue().getSanctionType());
  }
}
