package com.romrom.romback.domain.object.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
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
  @Schema(description = "회원ID", defaultValue = "")
  private UUID memberId;

  @Schema(description = "회원 상품 카테고리 매핑 리스트", defaultValue = "")
  private List<Integer> memberProductCategories;
}
