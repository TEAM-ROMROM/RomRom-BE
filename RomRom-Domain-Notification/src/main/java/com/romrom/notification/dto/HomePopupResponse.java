package com.romrom.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.romrom.notification.entity.HomePopup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@ToString
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class HomePopupResponse {

  // 노출 팝업 없음 > {"homePopup": null} 고정 (앱이 키 존재로 래퍼 형식 판별)
  @JsonInclude(JsonInclude.Include.ALWAYS)
  private HomePopup homePopup;
}
