package com.romrom.chat.entity.mongo;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 관리자 숨김 메시지 단위 테스트: 숨김·해제 상태, 회원 조회용 마스킹
 */
class ChatMessageHiddenTest {

  private static ChatMessage createImageMessageWithLocation() {
    return ChatMessage.builder()
        .chatMessageId("message-1")
        .chatRoomId(UUID.randomUUID())
        .senderId(UUID.randomUUID())
        .content("부적절한 내용")
        .imageUrls(List.of("https://image/1.png"))
        .latitude(37.5)
        .longitude(127.0)
        .type(MessageType.IMAGE)
        .build();
  }

  @Test
  @DisplayName("숨기지 않은 메시지는 마스킹해도 원본 그대로")
  void 숨기지_않은_메시지는_마스킹_안함() {
    ChatMessage chatMessage = createImageMessageWithLocation();
    chatMessage.maskIfHidden();

    Assertions.assertFalse(chatMessage.isHidden());
    Assertions.assertEquals("부적절한 내용", chatMessage.getContent());
    Assertions.assertEquals(1, chatMessage.getImageUrls().size());
    Assertions.assertEquals(37.5, chatMessage.getLatitude());
  }

  @Test
  @DisplayName("숨긴 메시지는 안내 문구로 바뀌고 이미지·위치가 빠진다")
  void 숨긴_메시지는_마스킹() {
    ChatMessage chatMessage = createImageMessageWithLocation();
    UUID adminId = UUID.randomUUID();
    chatMessage.hideByAdmin(adminId);
    chatMessage.maskIfHidden();

    Assertions.assertTrue(chatMessage.isHidden());
    Assertions.assertEquals(adminId, chatMessage.getHiddenByAdminId());
    Assertions.assertEquals(ChatMessage.HIDDEN_MESSAGE_PLACEHOLDER, chatMessage.getContent());
    Assertions.assertTrue(chatMessage.getImageUrls().isEmpty());
    Assertions.assertNull(chatMessage.getLatitude());
    Assertions.assertNull(chatMessage.getLongitude());
  }

  @Test
  @DisplayName("숨김 해제하면 숨김 정보가 지워져 마스킹되지 않는다")
  void 숨김_해제() {
    ChatMessage chatMessage = createImageMessageWithLocation();
    chatMessage.hideByAdmin(UUID.randomUUID());
    chatMessage.unhide();
    chatMessage.maskIfHidden();

    Assertions.assertFalse(chatMessage.isHidden());
    Assertions.assertNull(chatMessage.getHiddenAt());
    Assertions.assertNull(chatMessage.getHiddenByAdminId());
    Assertions.assertEquals("부적절한 내용", chatMessage.getContent());
  }
}
