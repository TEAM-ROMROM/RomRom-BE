package com.romrom.item.dto;

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

@ToString
@AllArgsConstructor
@Getter
@Setter
@Builder
@NoArgsConstructor
public class LikeRequest {

  @Schema(hidden = true, description = "회원")
  @JsonIgnore
  private Member member;

  @Schema(description = "물품 PK")
  private UUID itemId;
}
