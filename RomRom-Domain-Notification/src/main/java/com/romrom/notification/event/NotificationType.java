package com.romrom.notification.event;

import java.util.EnumSet;
import java.util.Set;
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

  // 거래 소식 확장 (#861) — 옛 앱(v1.10.142 이하) 알림함에는 숨김
  TRADE_REQUEST_ACCEPTED("교환 요청이 수락됐어요", "%s님이 '%s' 교환 요청을 수락했어요. 채팅을 시작해 보세요!"),
  TRADE_REQUEST_REJECTED("교환 요청이 거절됐어요", "'%s'에 보낸 교환 요청이 거절됐어요. 다른 물건도 둘러볼까요?"),
  TRADE_COMPLETED("교환이 완료됐어요", "%s님과의 교환이 완료됐어요. 후기를 남겨 주세요!"),
  TRADE_REVIEW_RECEIVED("새로운 후기가 도착했어요", "%s님이 교환 후기를 남겼어요. 지금 확인해 보세요!"),

  // 키워드 알림 (#873): 등록 키워드가 포함된 새 물품 — 옛 앱 알림함에는 숨김
  KEYWORD_ITEM_REGISTERED("키워드 알림", "'%s' 새 물품: %s"),

  // 광고성 푸시 캠페인 (#874): 마케팅 수신 동의자 대상 — 옛 앱 알림함에는 숨김
  MARKETING("이벤트·혜택", "%s"),
  ;

  // 옛 앱 알림함 숨김 대상: 모르는 타입을 공지로 표시 > 끄기 버튼이 콘텐츠·마케팅 동의를 꺼 버림 (#861)
  private static final Set<NotificationType> LEGACY_CLIENT_HIDDEN_TYPES = EnumSet.of(
      TRADE_REQUEST_ACCEPTED,
      TRADE_REQUEST_REJECTED,
      TRADE_COMPLETED,
      TRADE_REVIEW_RECEIVED,
      KEYWORD_ITEM_REGISTERED,
      MARKETING
  );

  private final String title;
  private final String body;

  /**
   * 옛 앱 알림함에서 제외할 타입 목록 (복사본)
   */
  public static Set<NotificationType> legacyClientHiddenTypes() {
    return EnumSet.copyOf(LEGACY_CLIENT_HIDDEN_TYPES);
  }
}
