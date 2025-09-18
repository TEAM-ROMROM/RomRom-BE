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
import java.util.Map;
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
  private boolean aiPrice;

  @Schema(description = "페이지 번호", defaultValue = "0")
  private int pageNumber;

  @Schema(description = "페이지 크기", defaultValue = "30")
  private int pageSize;

  @Schema(description = "정렬 기준")
  private ItemSortField sortField;

  @Schema(description = "정렬 방향")
  private Sort.Direction sortDirection;

  @Min(value = 0, message = "반경 값은 양수만 입력 가능합니다.")
  private double radiusInMeters;

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

  public ItemRequest() {
    this.pageNumber = 0;
    this.pageSize = 30;
    this.sortField = ItemSortField.CREATED_DATE;
    this.sortDirection = Direction.DESC;
  }

  public static ItemRequest fromParams(Map<String, String> params) {
    ItemRequest request = new ItemRequest();

    if (params.get("pageNumber") != null) {
      try {
        request.pageNumber = Integer.parseInt(params.get("pageNumber"));
      } catch (NumberFormatException e) {
        request.pageNumber = 0;
      }
    }

    if (params.get("pageSize") != null) {
      try {
        request.pageSize = Integer.parseInt(params.get("pageSize"));
      } catch (NumberFormatException e) {
        request.pageSize = 20;
      }
    }

    request.searchKeyword = params.get("searchKeyword");

    if (params.get("itemCategory") != null && !params.get("itemCategory").isEmpty()) {
      try {
        request.itemCategory = ItemCategory.valueOf(params.get("itemCategory"));
      } catch (IllegalArgumentException e) {
        request.itemCategory = null;
      }
    }

    if (params.get("itemCondition") != null && !params.get("itemCondition").isEmpty()) {
      try {
        request.itemCondition = ItemCondition.valueOf(params.get("itemCondition"));
      } catch (IllegalArgumentException e) {
        request.itemCondition = null;
      }
    }

    if (params.get("itemStatus") != null && !params.get("itemStatus").isEmpty()) {
      try {
        request.itemStatus = ItemStatus.valueOf(params.get("itemStatus"));
      } catch (IllegalArgumentException e) {
        request.itemStatus = null;
      }
    }

    if (params.get("minPrice") != null && !params.get("minPrice").isEmpty()) {
      try {
        request.minPrice = Integer.parseInt(params.get("minPrice"));
      } catch (NumberFormatException e) {
        request.minPrice = null;
      }
    }

    if (params.get("maxPrice") != null && !params.get("maxPrice").isEmpty()) {
      try {
        request.maxPrice = Integer.parseInt(params.get("maxPrice"));
      } catch (NumberFormatException e) {
        request.maxPrice = null;
      }
    }

    request.startDate = params.get("startDate");
    request.endDate = params.get("endDate");
    request.sortBy = params.getOrDefault("sortBy", "createdDate");

    if (params.get("sortDirection") != null && !params.get("sortDirection").isEmpty()) {
      try {
        request.sortDirection = Sort.Direction.valueOf(params.get("sortDirection"));
      } catch (IllegalArgumentException e) {
        request.sortDirection = Sort.Direction.DESC;
      }
    }

    return request;
  }
}
