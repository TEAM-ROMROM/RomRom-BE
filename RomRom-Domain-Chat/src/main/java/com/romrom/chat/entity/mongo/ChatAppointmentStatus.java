package com.romrom.chat.entity.mongo;

/**
 * 약속 상태: 제안 > 확정 또는 취소
 */
public enum ChatAppointmentStatus {
  PROPOSED,
  ACCEPTED,
  CANCELED,
  ;

  public boolean isCancelable() {
    return this == PROPOSED || this == ACCEPTED;
  }
}
