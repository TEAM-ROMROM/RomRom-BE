package com.romrom.member.entity;

import com.romrom.common.constant.ItemCategory;
import com.romrom.common.converter.ProductCategoryConverter;
import com.romrom.common.entity.postgres.BasePostgresEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.util.UUID;
import lombok.AllArgsConstructor;
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
public class MemberItemCategory extends BasePostgresEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(updatable = false, nullable = false)
  private UUID memberItemCategoryId;

  @ManyToOne(fetch = FetchType.LAZY)
  private Member member;

  // Integer -> ItemCategory 로 매핑 ( 실제 DB 저장은 Integer )
  @Convert(converter = ProductCategoryConverter.class)
  @Column(nullable = false)
  private ItemCategory itemCategory;
}
