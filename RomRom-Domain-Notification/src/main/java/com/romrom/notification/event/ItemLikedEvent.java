package com.romrom.notification.event;

import java.util.UUID;
import lombok.Getter;

/**
 * 내 게시글 좋아요 알림
 * - Title: {내 물품명} 에 새로운 좋아요
 * - Body: {상대 닉네임} 님이 회원님의 물품을 좋아해요!
 */
@Getter
public class ItemLikedEvent extends NotificationEvent {

  private final UUID itemId;
  private final String itemName;
  private final String senderNickname;

  public ItemLikedEvent (
    UUID targetMemberId,
    UUID itemId,
    String itemName,
    String senderNickname
  ) {
    super(targetMemberId, NotificationType.ITEM_LIKED);
    this.itemId = itemId;
    this.itemName = itemName;
    this.senderNickname = senderNickname;
    setDeepLink("romrom://item/detail?itemId=" + itemId);
  }

  @Override
  public String getTitle() {
    return String.format(getNotificationType().getTitle(), itemName);
  }

  @Override
  public String getBody() {
    return String.format(getNotificationType().getBody(), senderNickname);
  }
}
