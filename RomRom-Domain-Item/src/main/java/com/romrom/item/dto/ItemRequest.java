package com.romrom.item.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.romrom.common.constant.ItemCategory;
import com.romrom.common.constant.ItemCondition;
import com.romrom.common.constant.ItemStatus;
import com.romrom.common.constant.ItemTradeOption;
import com.romrom.member.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.web.multipart.MultipartFile;

@ToString
@AllArgsConstructor
@Getter
@Setter
@Builder
public class ItemRequest {

  @Schema(hidden = true, description = "회원")
  @JsonIgnore
  private Member member;

  @Schema(description = "물품 사진", required = false)
  private List<MultipartFile> itemImages = new ArrayList<>();

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

  @Schema(description = "커스텀 태그")
  @Builder.Default
  private List<String> itemCustomTags = new ArrayList<>();

  @Schema(description = "거래 희망 위치 경도")
  private Double longitude;

  @Schema(description = "거래 희망 위치 위도")
  private Double latitude;

  @Schema(description = "물품 ID")
  private UUID itemId;

  @Schema(description = "페이지 번호", defaultValue = "0")
  private int pageNumber;

  @Schema(description = "페이지 크기", defaultValue = "30")
  private int pageSize;

  public ItemRequest() {
    this.pageNumber = 0;
    this.pageSize = 30;
  }
}
