package com.romrom.notification.event;

import java.util.UUID;
import lombok.Getter;

/**
 * 거래 요청 알람
 * - Title: {내 물품명} 에 교환 요청!
 * - Body: {상대 닉네임} 님이 보낸 요청을 지금 확인해볼까요?
 */
@Getter
public class TradeRequestReceivedEvent extends NotificationEvent {

  private final String itemName; // 내 물품명
  private final String senderNickname;

  public TradeRequestReceivedEvent(
    UUID targetMemberId,
    String itemName,
    String senderNickname
  ) {
    super(targetMemberId, NotificationType.TRADE_REQUEST_RECEIVED);
    this.itemName = itemName;
    this.senderNickname = senderNickname;
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
