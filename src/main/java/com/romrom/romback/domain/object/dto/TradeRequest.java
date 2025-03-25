package com.romrom.romback.domain.object.dto;

import com.romrom.romback.domain.object.constant.TradeOption;
import com.romrom.romback.domain.object.postgres.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
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
public class TradeRequest {

  @Schema(description = "회원")
  private Member member;

  @Schema(description = "교환 요청을 받은 물품 (상대방 물품)")
  private UUID requestedItemId;

  @Schema(description = "교환 요청을 보낸 물품 (내 물품)")
  private UUID requestingItemId;

  @Schema(description = "상품 옵션 (추가금, 직거래만, 택배거래만")
  private List<TradeOption> tradeOptions = new ArrayList<>();
}
