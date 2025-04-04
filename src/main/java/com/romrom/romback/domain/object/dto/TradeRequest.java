package com.romrom.romback.domain.object.dto;

import com.romrom.romback.domain.object.constant.ItemTradeOption;
import com.romrom.romback.domain.object.postgres.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
@AllArgsConstructor
@Getter
@Setter
@Builder
public class TradeRequest {

  public TradeRequest() {
    this.pageNumber = 0;
    this.pageSize = 30;
  }

  @Schema(description = "회원")
  private Member member;

  @Schema(description = "교환 요청을 받은 물품")
  private UUID takeItemId;

  @Schema(description = "교환 요청을 보낸 물품")
  private UUID giveItemId;

  @Schema(description = "물품 옵션 (추가금, 직거래만, 택배거래만")
  private List<ItemTradeOption> itemTradeOptions = new ArrayList<>();

  @Schema(defaultValue = "0")
  @Min(value = 0, message = "페이지 번호 인덱스에 음수는 입력될 수 없습니다.")
  @Max(value = Integer.MAX_VALUE, message = "정수 최대 범위를 넘을 수 없습니다.")
  private Integer pageNumber; // 페이지 번호

  @Schema(defaultValue = "30")
  @Min(value = 0, message = "페이지 사이즈에 음수는 입력될 수 없습니다.")
  @Max(value = Integer.MAX_VALUE, message = "정수 최대 범위를 넘을 수 없습니다.")
  private Integer pageSize; // 페이지 사이즈
}
