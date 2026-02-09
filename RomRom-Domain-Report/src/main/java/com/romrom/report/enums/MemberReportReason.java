package com.romrom.report.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public enum MemberReportReason {
  RUDE_BEHAVIOR(1, "비매너/욕설/혐오/성적 발언"),
  FRAUD_SUSPICION(2, "사기 의심/거래 금지 물품"),
  FALSE_LISTING(3, "물건 상태 불일치/허위 매물"),
  NO_SHOW(4, "노쇼/약속 불이행"),
  ETC(5, "기타(직접입력)");

  private final int code;
  private final String description;

  private static final Map<Integer, MemberReportReason> MEMBER_REPORT_REASON_MAP;

  static {
    MEMBER_REPORT_REASON_MAP = Collections.unmodifiableMap(
        Arrays.stream(values())
            .collect(Collectors.toMap(MemberReportReason::getCode, Function.identity()))
    );
  }

  public static MemberReportReason fromCode(int code) {
    MemberReportReason memberReportReason = MEMBER_REPORT_REASON_MAP.get(code);
    if (memberReportReason == null) {
      throw new IllegalArgumentException("Invalid code: " + code);
    }
    return memberReportReason;
  }
}
