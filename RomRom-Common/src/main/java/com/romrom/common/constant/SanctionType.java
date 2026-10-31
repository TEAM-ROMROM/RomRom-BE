package com.romrom.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum SanctionType {
  SUSPEND("정지"),
  UNSUSPEND("정지해제"),
  FORCE_WITHDRAW("강제탈퇴"),
  BULK_DELETE_ITEMS("물품일괄삭제"),
  ADMIN_NOTIFICATION("관리자알림발송"),
  CHAT_MESSAGE_HIDE("채팅메시지숨김"),
  CHAT_MESSAGE_UNHIDE("채팅메시지숨김해제"),
  CHAT_ROOM_FORCE_CLOSE("채팅방강제종료"),
  CHAT_ROOM_FORCE_LEAVE("채팅방강제퇴장");

  private final String description;
}
