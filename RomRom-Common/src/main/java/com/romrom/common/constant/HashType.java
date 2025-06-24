package com.romrom.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum HashType {
  SERVER_ERROR_CODES("스프링 서버 에러코드 해시값"),
  ;

  private final String description;
}
