package com.romrom.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 관리자 작업 기록 대상 종류 (#874)
 */
@AllArgsConstructor
@Getter
public enum AdminAuditTargetType {
  SYSTEM_CONFIG("시스템 설정"),
  ANNOUNCEMENT("공지"),
  HOME_POPUP("홈 팝업"),
  ITEM("물품"),
  TRADE("거래"),
  REVIEW("후기"),
  REPORT("신고"),
  CHAT_ROOM("채팅방"),
  CHAT_MESSAGE("채팅 메시지"),
  MEMBER("회원"),
  MEMBER_BLOCK("회원 차단"),
  FCM_TOKEN("푸시 토큰"),
  PUSH_CAMPAIGN("푸시 캠페인"),
  ADMIN_ACCOUNT("관리자 계정");

  private final String description;
}
