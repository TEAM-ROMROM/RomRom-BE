package com.romrom.romback.domain.object.dto;

import com.romrom.romback.domain.object.postgres.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@ToString
@AllArgsConstructor
@Getter
@Setter
@Builder
@NoArgsConstructor
public class MemberRequest {
  @Schema(description = "Member 내부값 값을 전체 삭제하여서 테스트 해주세요")
  private Member member;

  @Schema(description = "회원 상품 카테고리 매핑 리스트", defaultValue = "")
  private List<Integer> preferredCategories;
}
