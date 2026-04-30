package com.romrom.item.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.romrom.common.constant.ItemTradeOption;
import com.romrom.common.constant.TradeRequestSortField;
import com.romrom.common.constant.TradeReviewRating;
import com.romrom.common.constant.TradeReviewTag;
import com.romrom.member.entity.Member;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

@ToString
@AllArgsConstructor
@Getter
@Setter
@Builder
public class TradeRequest {

  // 생성자
  public TradeRequest() {
    this.pageNumber = 0;
    this.pageSize = 30;
    this.sortField = TradeRequestSortField.CREATED_DATE;
    this.sortDirection = Direction.DESC;
  }

  @Schema(hidden = true, description = "회원")
  @JsonIgnore
  private Member member;

  @Schema(description = "교환 요청을 받은 물품")
  private UUID takeItemId;

  @Schema(description = "교환 요청을 보낸 물품")
  private UUID giveItemId;

  @Schema(description = "거래 요청 ID")
  private UUID tradeRequestHistoryId;

  @Schema(description = "물품 옵션 (추가금, 직거래만, 택배거래만")
  @Builder.Default
  private List<ItemTradeOption> itemTradeOptions = new ArrayList<>();

  @Schema(defaultValue = "0")
  @Min(value = 0, message = "페이지 번호 인덱스에 음수는 입력될 수 없습니다.")
  @Max(value = Integer.MAX_VALUE, message = "정수 최대 범위를 넘을 수 없습니다.")
  private Integer pageNumber; // 페이지 번호

  @Schema(defaultValue = "30")
  @Min(value = 0, message = "페이지 사이즈에 음수는 입력될 수 없습니다.")
  @Max(value = Integer.MAX_VALUE, message = "정수 최대 범위를 넘을 수 없습니다.")
  private Integer pageSize; // 페이지 사이즈

  // 후기 작성 필드
  @Schema(description = "종합 평가 (BAD/GOOD/GREAT)")
  private TradeReviewRating tradeReviewRating;

  @Schema(description = "세부 항목 태그 목록")
  @Builder.Default
  private List<TradeReviewTag> tradeReviewTags = new ArrayList<>();

  @Schema(description = "한마디 (최대 200자, 선택)")
  private String reviewComment;

  @Schema(description = "거래 요청 목록 정렬 기준 (CREATED_DATE, PRICE, AI_RECOMMENDED)", defaultValue = "CREATED_DATE")
  private TradeRequestSortField sortField;

  @Schema(description = "정렬 방향 (ASC, DESC). AI_RECOMMENDED 정렬에서는 무시됩니다.", defaultValue = "DESC")
  private Sort.Direction sortDirection;
}
