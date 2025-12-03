package com.romrom.item.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.romrom.common.constant.ItemCategory;
import com.romrom.common.constant.ItemCondition;
import com.romrom.common.constant.ItemSortField;
import com.romrom.common.constant.ItemStatus;
import com.romrom.common.constant.ItemTradeOption;
import com.romrom.member.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;
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
public class ItemRequest {

  // 생성자
  public ItemRequest() {
    this.pageNumber = 0;
    this.pageSize = 30;
    this.sortField = ItemSortField.CREATED_DATE;
    this.sortDirection = Direction.DESC;
  }

  @Schema(hidden = true, description = "회원")
  @JsonIgnore
  private Member member;

  @Schema(description = "물품 사진 URL")
  private List<String> itemImageUrls = new ArrayList<>();

  @Schema(description = "물품명")
  private String itemName;

  @Schema(description = "물품 거래 상태")
  private ItemStatus itemStatus;

  @Schema(description = "물품 상세 설명")
  private String itemDescription;

  @Schema(description = "물품 카테고리")
  private ItemCategory itemCategory;

  @Schema(description = "물품 외관 상태")
  private ItemCondition itemCondition;

  @Schema(description = "물품 옵션 (추가금, 직거래만, 택배거래만)")
  private List<ItemTradeOption> itemTradeOptions = new ArrayList<>();

  @Schema(description = "가격")
  private Integer itemPrice;

  @Schema(description = "거래 희망 위치 경도")
  private Double longitude;

  @Schema(description = "거래 희망 위치 위도")
  private Double latitude;

  @Schema(description = "물품 ID")
  private UUID itemId;

  @Schema(description = "AI 가격측정 여부", defaultValue = "false")
  private Boolean isAiPredictedPrice;

  @Schema(description = "페이지 번호", defaultValue = "0")
  private int pageNumber;

  @Schema(description = "페이지 크기", defaultValue = "30")
  private int pageSize;

  @Schema(description = "정렬 기준")
  private ItemSortField sortField;

  @Schema(description = "정렬 방향")
  private Sort.Direction sortDirection;

  @Min(value = 0, message = "반경 값은 양수만 입력 가능합니다.")
  private Double radiusInMeters;

  @Schema(description = "검색 키워드")
  private String searchKeyword;

  @Schema(description = "최소 가격")
  private Integer minPrice;

  @Schema(description = "최대 가격") 
  private Integer maxPrice;

  @Schema(description = "시작일")
  private String startDate;

  @Schema(description = "종료일")
  private String endDate;

  @Schema(description = "정렬 필드")
  private String sortBy;
}
