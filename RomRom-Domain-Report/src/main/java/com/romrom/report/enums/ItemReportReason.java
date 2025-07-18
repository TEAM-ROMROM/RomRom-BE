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
public enum ItemReportReason {
  FRAUD(1, "허위 정보/사기 의심"),
  ILLEGAL_ITEM(2,"불법·금지 물품"),
  INAPPROPRIATE_CONTENT(3, "부적절한 컨텐츠 (욕설·폭력 등)"),
  SPAM_AD(4, "스팸·광고"),
  ETC(5, "기타 (extraComment 사용)");

  private final int code;
  private final String description;

  private static final Map<Integer, ItemReportReason> ITEM_REPORT_REASON_MAP;

  static {
    ITEM_REPORT_REASON_MAP = Collections.unmodifiableMap(
        Arrays.stream(values())
            .collect(Collectors.toMap(ItemReportReason::getCode, Function.identity()))
    );
  }

  public static ItemReportReason fromCode(int code) {
    ItemReportReason itemReportReason = ITEM_REPORT_REASON_MAP.get(code);
    if (itemReportReason == null) {
      throw new IllegalArgumentException("Invalid code: " + code);
    }
    return itemReportReason;
  }
}