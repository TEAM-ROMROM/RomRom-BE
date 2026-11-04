package com.romrom.chat.entity.mongo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 삭제 메시지 회원 조회 마스킹: 안내 문구·TEXT 전환(옛 앱 호환)·첨부 제거, 숨김 문구 우선
 */
class ChatMessageDeletedMaskTest {

  private static ChatMessage createImageMessage() {
    return ChatMessage.builder()
        .chatMessageId("message-1")
        .chatRoomId(UUID.randomUUID())
        .senderId(UUID.randomUUID())
        .content("사진을 보냈습니다.")
        .imageUrls(List.of("https://image/1.png"))
        .type(MessageType.IMAGE)
        .build();
  }

  @Test
  @DisplayName("삭제 메시지는 안내 문구 + TEXT + 사진 제거")
  void 삭제_메시지_마스킹() {
    ChatMessage chatMessage = createImageMessage();
    chatMessage.markDeleted();

    chatMessage.maskForMemberView();

    Assertions.assertTrue(chatMessage.isDeleted());
    Assertions.assertEquals(ChatMessage.DELETED_MESSAGE_PLACEHOLDER, chatMessage.getContent());
    Assertions.assertEquals(MessageType.TEXT, chatMessage.getType());
    Assertions.assertTrue(chatMessage.getImageUrls().isEmpty());
  }

  @Test
  @DisplayName("삭제와 숨김이 함께면 숨김 문구 유지")
  void 숨김_문구_우선() {
    ChatMessage chatMessage = createImageMessage();
    chatMessage.markDeleted();
    chatMessage.hideByAdmin(UUID.randomUUID());

    chatMessage.maskForMemberView();

    Assertions.assertEquals(ChatMessage.HIDDEN_MESSAGE_PLACEHOLDER, chatMessage.getContent());
  }

  @Test
  @DisplayName("삭제·숨김 아닌 약속 메시지는 그대로")
  void 약속_메시지_유지() {
    ChatAppointment appointment = ChatAppointment.propose(LocalDateTime.now().plusDays(1), "군자역", null, null,
        UUID.randomUUID());
    ChatMessage chatMessage = ChatMessage.builder().content("[약속] ...").type(MessageType.TEXT)
        .appointment(appointment).build();

    chatMessage.maskForMemberView();

    Assertions.assertSame(appointment, chatMessage.getAppointment());
    Assertions.assertEquals("[약속] ...", chatMessage.getContent());
  }
}
