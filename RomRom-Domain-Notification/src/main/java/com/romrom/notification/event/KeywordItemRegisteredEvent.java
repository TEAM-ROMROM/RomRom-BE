package com.romrom.notification.event;

import java.util.UUID;
import lombok.Getter;

/**
 * 키워드 알림 (#873): 등록 키워드가 포함된 새 물품
 * - Title: 키워드 알림
 * - Body: '{keyword}' 새 물품: {itemName}
 * - payload: itemId, keyword, deepLink(물품 상세)
 */
@Getter
public class KeywordItemRegisteredEvent extends NotificationEvent {

  private final UUID itemId;
  private final String itemName;
  private final String keyword;

  public KeywordItemRegisteredEvent(UUID targetMemberId, UUID itemId, String itemName, String keyword) {
    super(targetMemberId, NotificationType.KEYWORD_ITEM_REGISTERED);
    this.itemId = itemId;
    this.itemName = itemName;
    this.keyword = keyword;
    addPayload("itemId", itemId);
    addPayload("keyword", keyword);
    setDeepLink(ITEM_DETAIL_DEEP_LINK_PREFIX + itemId);
  }

  @Override
  public String getTitle() {
    return getNotificationType().getTitle();
  }

  @Override
  public String getBody() {
    return String.format(getNotificationType().getBody(), keyword, itemName);
  }
}
