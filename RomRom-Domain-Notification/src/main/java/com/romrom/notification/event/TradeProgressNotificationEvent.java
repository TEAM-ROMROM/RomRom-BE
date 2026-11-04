package com.romrom.notification.event;

import java.util.UUID;
import lombok.Getter;

/**
 * 거래 진행 알림 4종: 요청 수락, 요청 거절, 교환 완료, 후기 도착 (#861)
 * 종류별 정적 생성 메서드 > 제목·본문·딥링크·payload 확정, 발송은 리스너 1곳
 */
@Getter
public class TradeProgressNotificationEvent extends NotificationEvent {

  // 딥링크: 채팅방·물품 상세는 옛 앱도 아는 경로(NotificationEvent 공통), 후기는 새 경로 (옛 앱은 알림함으로 폴백)
  private static final String RECEIVED_REVIEWS_DEEP_LINK = "romrom://profile/reviews";

  private final UUID tradeRequestHistoryId;
  private final String title;
  private final String body;

  private TradeProgressNotificationEvent(
      UUID targetMemberId,
      NotificationType notificationType,
      UUID tradeRequestHistoryId,
      String title,
      String body
  ) {
    super(targetMemberId, notificationType);
    this.tradeRequestHistoryId = tradeRequestHistoryId;
    this.title = title;
    this.body = body;
    addPayload("tradeRequestHistoryId", tradeRequestHistoryId);
  }

  /**
   * 교환 요청 수락 (받은 사람이 채팅 시작) > 요청 보낸 사람에게, 채팅방으로 이동
   */
  public static TradeProgressNotificationEvent accepted(
      UUID requesterMemberId,
      UUID tradeRequestHistoryId,
      UUID chatRoomId,
      String accepterNickname,
      String requestedItemName
  ) {
    TradeProgressNotificationEvent event = ofType(requesterMemberId, NotificationType.TRADE_REQUEST_ACCEPTED,
        tradeRequestHistoryId, accepterNickname, requestedItemName);
    event.addPayload("chatRoomId", chatRoomId);
    event.setDeepLink(CHAT_ROOM_DEEP_LINK_PREFIX + chatRoomId);
    return event;
  }

  /**
   * 교환 요청 거절 > 요청 보낸 사람에게, 요청했던 상대 물품 상세로 이동
   */
  public static TradeProgressNotificationEvent rejected(
      UUID requesterMemberId,
      UUID tradeRequestHistoryId,
      UUID requestedItemId,
      String requestedItemName
  ) {
    TradeProgressNotificationEvent event = ofType(requesterMemberId, NotificationType.TRADE_REQUEST_REJECTED,
        tradeRequestHistoryId, requestedItemName);
    event.addPayload("itemId", requestedItemId);
    event.setDeepLink(ITEM_DETAIL_DEEP_LINK_PREFIX + requestedItemId);
    return event;
  }

  /**
   * 채팅방 교환 완료 확정 > 완료를 요청했던 상대에게, 채팅방으로 이동
   */
  public static TradeProgressNotificationEvent completedInChatRoom(
      UUID targetMemberId,
      UUID tradeRequestHistoryId,
      UUID chatRoomId,
      String partnerNickname
  ) {
    TradeProgressNotificationEvent event = ofType(targetMemberId, NotificationType.TRADE_COMPLETED,
        tradeRequestHistoryId, partnerNickname);
    event.addPayload("chatRoomId", chatRoomId);
    event.setDeepLink(CHAT_ROOM_DEEP_LINK_PREFIX + chatRoomId);
    return event;
  }

  /**
   * 채팅 없이 바로 교환 완료 > 요청 보낸 사람에게, 상대 물품 상세로 이동
   */
  public static TradeProgressNotificationEvent completedWithoutChatRoom(
      UUID targetMemberId,
      UUID tradeRequestHistoryId,
      UUID partnerItemId,
      String partnerNickname
  ) {
    TradeProgressNotificationEvent event = ofType(targetMemberId, NotificationType.TRADE_COMPLETED,
        tradeRequestHistoryId, partnerNickname);
    event.addPayload("itemId", partnerItemId);
    event.setDeepLink(ITEM_DETAIL_DEEP_LINK_PREFIX + partnerItemId);
    return event;
  }

  /**
   * 거래 후기 도착 > 후기 받은 사람에게, 내 받은 후기로 이동
   */
  public static TradeProgressNotificationEvent reviewReceived(
      UUID reviewedMemberId,
      UUID tradeRequestHistoryId,
      String reviewerNickname
  ) {
    TradeProgressNotificationEvent event = ofType(reviewedMemberId, NotificationType.TRADE_REVIEW_RECEIVED,
        tradeRequestHistoryId, reviewerNickname);
    event.setDeepLink(RECEIVED_REVIEWS_DEEP_LINK);
    return event;
  }

  // 종류별 공통 생성: 제목 = 타입 제목, 본문 = 타입 본문 서식 + 인자 (딥링크·추가 payload는 호출부)
  private static TradeProgressNotificationEvent ofType(
      UUID targetMemberId,
      NotificationType notificationType,
      UUID tradeRequestHistoryId,
      Object... bodyArguments
  ) {
    return new TradeProgressNotificationEvent(
        targetMemberId,
        notificationType,
        tradeRequestHistoryId,
        notificationType.getTitle(),
        String.format(notificationType.getBody(), bodyArguments)
    );
  }
}
