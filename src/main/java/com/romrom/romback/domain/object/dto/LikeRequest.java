package com.romrom.romback.domain.object.dto;


import com.romrom.romback.domain.object.postgres.Member;
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

  @Schema(description = "회원")
  private Member member;

  @Schema(description = "물품 PK")
  private UUID itemId;
}
