package com.romrom.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 관리자 작업 기록 종류 (#874): description = 작업 기록 요약 문구
 * DB 저장은 이름(STRING), 순서 변경 무관
 */
@AllArgsConstructor
@Getter
public enum AdminAuditAction {
  // 설정
  CONFIG_UPDATE("설정 변경"),
  CONFIG_CACHE_RELOAD("설정 캐시 새로고침"),
  ALERT_CONFIG_UPDATE("알림 설정 변경"),

  // 공지·팝업
  ANNOUNCEMENT_CREATE("공지 등록"),
  ANNOUNCEMENT_UPDATE("공지 수정"),
  ANNOUNCEMENT_DELETE("공지 삭제"),
  HOME_POPUP_CREATE("홈 팝업 등록"),
  HOME_POPUP_UPDATE("홈 팝업 수정"),
  HOME_POPUP_DELETE("홈 팝업 삭제"),

  // 물품
  ITEM_DELETE("물품 삭제"),
  ITEM_STATUS_UPDATE("물품 거래 상태 변경"),
  ITEM_UPDATE("물품 수정"),
  ITEM_HIDE("물품 숨김"),
  ITEM_UNHIDE("물품 숨김 해제"),
  ITEM_EMBEDDING_REGENERATE("물품 임베딩 재생성"),
  ITEM_PROMOTION_END("물품 롬업 종료"),

  // 거래·후기
  TRADE_FORCE_CANCEL("거래 강제 취소"),
  TRADE_FORCE_COMPLETE("거래 강제 완료"),
  REVIEW_BLIND("후기 블라인드"),
  REVIEW_UNBLIND("후기 블라인드 해제"),

  // 신고
  REPORT_STATUS_UPDATE("신고 상태 변경"),
  REPORT_RESOLVE("신고 처리"),

  // 채팅
  CHAT_ROOM_FORCE_DELETE("채팅방 강제 삭제"),
  CHAT_ROOM_FORCE_CLOSE("채팅방 강제 종료"),
  CHAT_MESSAGE_HIDE("채팅 메시지 숨김"),
  CHAT_MESSAGE_UNHIDE("채팅 메시지 숨김 해제"),
  CHAT_MEMBER_FORCE_LEAVE("채팅방 회원 강제 퇴장"),
  CHAT_ROOM_EXPORT("채팅방 대화 내보내기"),

  // 회원
  MEMBER_FORCE_WITHDRAW("회원 강제 탈퇴"),
  MEMBER_BULK_SUSPEND("회원 일괄 정지"),
  MEMBER_BULK_WITHDRAW("회원 일괄 탈퇴"),
  MEMBER_ITEMS_BULK_DELETE("회원 물품 일괄 삭제"),
  MEMBER_NOTIFICATION_SEND("회원 알림 발송"),
  MEMBER_STATUS_UPDATE("회원 상태 변경"),
  MEMBER_SUSPEND("회원 정지"),
  MEMBER_UNSUSPEND("회원 정지 해제"),
  MEMBER_PROFILE_UPDATE("회원 정보 수정"),
  MEMBER_BLOCK_DELETE("회원 차단 해제"),
  MEMBER_FCM_TOKEN_DELETE("회원 푸시 토큰 삭제"),

  // 푸시 캠페인
  PUSH_CAMPAIGN_CREATE("푸시 발송·예약"),
  PUSH_CAMPAIGN_TEST_SEND("푸시 테스트 발송"),
  PUSH_CAMPAIGN_CANCEL("푸시 예약 취소"),

  // 관리자 계정
  ADMIN_PASSWORD_CHANGE("관리자 비밀번호 변경"),

  // CSV 내보내기
  CSV_EXPORT_MEMBERS("회원 CSV 내보내기"),
  CSV_EXPORT_ITEMS("물품 CSV 내보내기"),
  CSV_EXPORT_ITEM_REPORTS("물품 신고 CSV 내보내기"),
  CSV_EXPORT_MEMBER_REPORTS("회원 신고 CSV 내보내기"),
  CSV_EXPORT_CHAT_MESSAGE_REPORTS("채팅 신고 CSV 내보내기");

  private final String description;
}
