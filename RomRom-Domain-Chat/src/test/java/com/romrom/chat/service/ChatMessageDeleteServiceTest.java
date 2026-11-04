package com.romrom.chat.service;

import com.romrom.chat.entity.mongo.ChatAppointment;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.mongo.MessageType;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 내 메시지 삭제 가능 판정: 보낸 사람, 일반 메시지, 24시간 이내
 */
class ChatMessageDeleteServiceTest {

  private final UUID senderId = UUID.randomUUID();
  private final LocalDateTime now = LocalDateTime.of(2026, 11, 5, 12, 0);

  private ChatMessage createMessage(MessageType type, LocalDateTime sentAt) {
    ChatMessage chatMessage = ChatMessage.builder().senderId(senderId).type(type).content("안녕하세요").build();
    ReflectionTestUtils.setField(chatMessage, "createdDate", sentAt);
    return chatMessage;
  }

  private ErrorCode assertRejected(ChatMessage chatMessage, UUID memberId) {
    return Assertions.assertThrows(CustomException.class,
        () -> ChatMessageDeleteService.validateDeletable(chatMessage, memberId, now)).getErrorCode();
  }

  @Test
  @DisplayName("보낸 지 24시간 이내 내 TEXT 메시지는 삭제 가능")
  void 삭제_가능() {
    Assertions.assertDoesNotThrow(() ->
        ChatMessageDeleteService.validateDeletable(createMessage(MessageType.TEXT, now.minusHours(23)), senderId, now));
  }

  @Test
  @DisplayName("상대 메시지 > CHAT_MESSAGE_DELETE_NOT_SENDER")
  void 상대_메시지_거부() {
    Assertions.assertEquals(ErrorCode.CHAT_MESSAGE_DELETE_NOT_SENDER,
        assertRejected(createMessage(MessageType.TEXT, now), UUID.randomUUID()));
  }

  @Test
  @DisplayName("24시간 지난 메시지 > CHAT_MESSAGE_DELETE_PERIOD_EXPIRED")
  void 기간_지남_거부() {
    Assertions.assertEquals(ErrorCode.CHAT_MESSAGE_DELETE_PERIOD_EXPIRED,
        assertRejected(createMessage(MessageType.IMAGE, now.minusHours(24).minusMinutes(1)), senderId));
  }

  @Test
  @DisplayName("시스템·이미 삭제·약속 메시지 > CHAT_MESSAGE_NOT_DELETABLE")
  void 삭제_불가_메시지_거부() {
    Assertions.assertEquals(ErrorCode.CHAT_MESSAGE_NOT_DELETABLE,
        assertRejected(createMessage(MessageType.SYSTEM, now), senderId));

    ChatMessage deletedMessage = createMessage(MessageType.TEXT, now);
    deletedMessage.markDeleted();
    Assertions.assertEquals(ErrorCode.CHAT_MESSAGE_NOT_DELETABLE, assertRejected(deletedMessage, senderId));

    ChatMessage appointmentMessage = ChatMessage.builder().senderId(senderId).type(MessageType.TEXT)
        .appointment(ChatAppointment.propose(now.plusDays(1), "군자역", null, null, senderId)).build();
    ReflectionTestUtils.setField(appointmentMessage, "createdDate", now);
    Assertions.assertEquals(ErrorCode.CHAT_MESSAGE_NOT_DELETABLE, assertRejected(appointmentMessage, senderId));
  }
}
