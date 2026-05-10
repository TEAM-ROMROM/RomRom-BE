package com.romrom.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Role {

  ROLE_USER("일반 회원"),
  ROLE_ADMIN("관리자"),
  ROLE_TEST("테스트 회원"); // dev 환경 전용 — 개발자 도구에서 생성한 테스트 계정

  private final String description;
}
