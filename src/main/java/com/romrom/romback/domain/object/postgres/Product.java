package com.romrom.romback.domain.object.postgres;

import com.romrom.romback.domain.object.constant.ProductCategory;
import com.romrom.romback.domain.object.constant.ProductCondition;
import com.romrom.romback.domain.object.constant.ProductOption;
import com.romrom.romback.global.converter.ProductCategoryConverter;
import com.romrom.romback.global.util.BasePostgresEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
public class Product extends BasePostgresEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID productId; // PK

  @ManyToOne(fetch = FetchType.LAZY)
  private Member member;

  @Column(nullable = false)
  private String productName; // 상품명

  private String productDescription; // 상품 상세설명

  // Integer -> ProductCategory 로 매핑 ( 실제 DB 저장은 Integer )
  @Convert(converter = ProductCategoryConverter.class)
  private ProductCategory productCategory; // 상품 카테고리

  @Enumerated(EnumType.STRING)
  private ProductCondition productCondition; // 상품 상태

  @Enumerated(EnumType.STRING)
  private ProductOption productOption; // 옵션 (추가금, 직거래만, 택배거래만)

  @Builder.Default
  private Integer likes = 0; // 좋아요 수

  @Builder.Default
  private Integer price = 0; // 가격

  // TODO: 거래 희망 장소
}
