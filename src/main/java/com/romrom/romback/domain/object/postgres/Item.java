package com.romrom.romback.domain.object.postgres;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.romrom.romback.domain.object.constant.ItemCategory;
import com.romrom.romback.domain.object.constant.ItemCondition;
import com.romrom.romback.domain.object.constant.ItemTradeOption;
import com.romrom.romback.global.converter.ProductCategoryConverter;
import com.romrom.romback.global.util.BasePostgresEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Item extends BasePostgresEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID itemId; // PK

  @ManyToOne(fetch = FetchType.LAZY)
  private Member member;

  @Column(nullable = false)
  private String itemName; // 상품명

  private String itemDescription; // 상품 상세설명

  // Integer -> ItemCategory 로 매핑 ( 실제 DB 저장은 Integer )
  @Convert(converter = ProductCategoryConverter.class)
  private ItemCategory itemCategory; // 상품 카테고리

  @Enumerated(EnumType.STRING)
  private ItemCondition itemCondition; // 상품 상태

  @ElementCollection
  private List<ItemTradeOption> itemTradeOptions = new ArrayList<>(); // 옵션 (추가금, 직거래만, 택배거래만)

  @Builder.Default
  private Integer likeCount = 0; // 좋아요 수

  @Builder.Default
  private Integer price = 0; // 가격

  // TODO: 거래 희망 장소

  public void increaseLikeCount() {
    likeCount++;
  }
  public void decreaseLikeCount() {
    likeCount--;
    if(likeCount < 0) likeCount = 0;
    log.warn("좋아요 개수 0개 처리 : 좋아요 개수는 음수가 될 수 없습니다.");
  }
}
