package com.romrom.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum TradeReviewTag {
  FAST_RESPONSE("답장이 빨라요"),
  GOOD_ITEM_CONDITION("물건 상태가 좋아요"),
  MATCHES_PHOTO("사진과 같아요"),
  PUNCTUAL("약속을 잘 지켜요"),
  KIND("친절해요"),
  ;

  private final String description;
}
