package com.romrom.member.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.romrom.member.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 키워드 알림 요청 (#873): 등록 keyword, 삭제 keywordAlertId (multipart form)
 */
@ToString(exclude = "member")
@AllArgsConstructor
@Getter
@Setter
@Builder
@NoArgsConstructor
public class MemberKeywordAlertRequest {

  @Schema(hidden = true, description = "회원")
  @JsonIgnore
  private Member member;

  @Schema(description = "등록할 키워드 (앞뒤·연속 공백 정리 후 2~20자)")
  private String keyword;

  @Schema(description = "삭제할 키워드 알림 ID")
  private UUID keywordAlertId;
}
