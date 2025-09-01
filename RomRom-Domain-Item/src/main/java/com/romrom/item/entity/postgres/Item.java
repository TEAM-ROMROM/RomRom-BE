package com.romrom.item.entity.postgres;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.romrom.common.constant.ItemCategory;
import com.romrom.common.constant.ItemCondition;
import com.romrom.common.constant.ItemStatus;
import com.romrom.common.constant.ItemTradeOption;
import com.romrom.common.converter.ProductCategoryConverter;
import com.romrom.common.entity.postgres.BasePostgresEntity;
import com.romrom.common.util.LocationUtil;
import com.romrom.item.dto.ItemRequest;
import com.romrom.member.entity.Member;
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
import org.hibernate.annotations.Where;
import org.locationtech.jts.geom.Point;

@Slf4j
@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
//@SQLDelete(sql = "UPDATE item SET isDeleted = true WHERE item_id = ?")  // delete() 호출 시 update 쿼리 실행 (즉 delete → update)
@Where(clause = "is_deleted = false")          // 자동 조회 제한
public class Item extends BasePostgresEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID itemId; // PK

  @ManyToOne(fetch = FetchType.LAZY)
  private Member member;

  @Column(nullable = false)
  private String itemName; // 물품명

  @Column(length = 2048)
  private String itemDescription; // 물품 상세설명

  // Integer -> ItemCategory 로 매핑 ( 실제 DB 저장은 Integer )
  @Convert(converter = ProductCategoryConverter.class)
  private ItemCategory itemCategory; // 물품 카테고리

  @Enumerated(EnumType.STRING)
  private ItemCondition itemCondition; // 물품 외관 상태

  @Enumerated(EnumType.STRING)
  private ItemStatus itemStatus; // 물품 거래 상태

  @ElementCollection
  private List<ItemTradeOption> itemTradeOptions = new ArrayList<>(); // 옵션 (추가금, 직거래만, 택배거래만)

  @Column(columnDefinition = "geometry(Point, 4326)")
  @JsonIgnore
  private Point location; // 거래 희망 위치

  @Builder.Default
  private Integer likeCount = 0; // 좋아요 수

  @Builder.Default
  private Integer price = 0; // 가격

  @Builder.Default
  private boolean aiPrice = false; // AI 가격측정 여부

  // TODO: 거래 희망 장소

  public void increaseLikeCount() {
    likeCount++;
  }
  public void decreaseLikeCount() {
    likeCount--;
    if(likeCount < 0) likeCount = 0;
    log.warn("좋아요 개수 0개 처리 : 좋아요 개수는 음수가 될 수 없습니다.");
  }

  // 아이템 삭제 여부
  @Column(nullable = false)
  @Builder.Default
  @JsonIgnore
  private Boolean isDeleted = false;

  // JSON 직렬화를 위한 위도/경도 getter 메서드
  @JsonProperty("longitude")
  public Double getLongitude() {
    return location != null ? location.getX() : null;
  }

  @JsonProperty("latitude")
  public Double getLatitude() {
    return location != null ? location.getY() : null;
  }

  public static Item fromItemRequest(ItemRequest request) {
    return Item.builder()
        .member(request.getMember())
        .itemName(request.getItemName())
        .itemDescription(request.getItemDescription())
        .itemCategory(request.getItemCategory())
        .itemCondition(request.getItemCondition())
        .itemStatus(request.getItemStatus())
        .itemTradeOptions(request.getItemTradeOptions())
        .location(LocationUtil.convertToPoint(request.getLongitude(), request.getLatitude()))
        .price(request.getItemPrice())
        .likeCount(0)
        .aiPrice(request.isAiPrice())
        .build();
  }
}
