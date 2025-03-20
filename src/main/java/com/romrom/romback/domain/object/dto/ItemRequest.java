package com.romrom.romback.domain.object.dto;

import com.romrom.romback.domain.object.constant.ItemCategory;
import com.romrom.romback.domain.object.constant.ItemCondition;
import com.romrom.romback.domain.object.constant.TradeOption;
import com.romrom.romback.domain.object.postgres.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.web.multipart.MultipartFile;

@ToString
@AllArgsConstructor
@Getter
@Setter
@Builder
@NoArgsConstructor
public class ItemRequest {

  @Schema(description = "회원")
  private Member member;

  @Schema(description = "상품 사진")
  private List<MultipartFile> itemImages = new ArrayList<>();

  @Schema(description = "상품명")
  private String itemName;

  @Schema(description = "상품 상세 설명")
  private String itemDescription;

  @Schema(description = "상품 카테고리")
  private ItemCategory itemCategory;

  @Schema(description = "상품 상태")
  private ItemCondition itemCondition;

  @Schema(description = "상품 옵션 (추가금, 직거래만, 택배거래만)")
  private List<TradeOption> tradeOptions = new ArrayList<>();

  @Schema(description = "가격")
  private Integer price;
}
