package com.romrom.notification.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {

  // 거래
  TRADE_REQUEST_RECEIVED("%s에 교환 요청", "%s님이 보낸 요청을 지금 확인해볼까요?"),

  // 채팅
  CHAT_MESSAGE_RECEIVED("%s", "%s"),

  // 좋아요
  ITEM_LIKED("%s에 새로운 좋아요", "%s 님이 회원님의 물품을 좋아해요!"),

  // 공지
  ANNOUNCEMENT("롬롬이 알려드려요!", "%s - 새로운 소식을 확인해 보세요!"),

  // 관리자 처리
  ITEM_DELETED_BY_ADMIN("관리자 알림", "관리자에 의해 물품이 삭제되었습니다"),
  ;
  private final String title;
  private final String body;
}
