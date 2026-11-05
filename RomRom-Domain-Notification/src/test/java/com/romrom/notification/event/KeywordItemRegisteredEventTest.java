package com.romrom.notification.event;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 키워드 알림 이벤트 (#873): 제목·본문 서식, payload(itemId·keyword·딥링크·타입)
 */
class KeywordItemRegisteredEventTest {

  @Test
  @DisplayName("제목 '키워드 알림', 본문 '{키워드}' 새 물품: {물품명}, 물품 상세 딥링크")
  void keywordItemRegistered_문구와payload() {
    UUID targetMemberId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();

    KeywordItemRegisteredEvent event = new KeywordItemRegisteredEvent(targetMemberId, itemId, "닌텐도 스위치 OLED", "닌텐도");
    Map<String, String> payload = event.getPayload();

    Assertions.assertEquals("키워드 알림", event.getTitle());
    Assertions.assertEquals("'닌텐도' 새 물품: 닌텐도 스위치 OLED", event.getBody());
    Assertions.assertEquals(targetMemberId, event.getTargetMemberId());
    Assertions.assertEquals("KEYWORD_ITEM_REGISTERED", payload.get("notificationType"));
    Assertions.assertEquals(itemId.toString(), payload.get("itemId"));
    Assertions.assertEquals("닌텐도", payload.get("keyword"));
    Assertions.assertEquals("romrom://item/detail?itemId=" + itemId, payload.get("deepLink"));
  }
}
