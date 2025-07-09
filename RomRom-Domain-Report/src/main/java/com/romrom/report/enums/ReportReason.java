package com.romrom.report.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ReportReason {
  FRAUD,                // 허위 정보/사기 의심
  ILLEGAL_GOODS,        // 불법·금지 물품
  INAPPROPRIATE_CONTENT,// 부적절한 컨텐츠 (욕설·폭력 등)
  SPAM_AD,              // 스팸·광고
  ETC                   // 기타 (extraComment 사용)
}