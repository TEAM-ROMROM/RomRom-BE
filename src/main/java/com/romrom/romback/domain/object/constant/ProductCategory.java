package com.romrom.romback.domain.object.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProductCategory {
  WOMEN_CLOTHING(1, "여성의류"),
  MEN_CLOTHING(2, "남성의류"),
  SHOES(3, "신발"),
  BAGS_WALLETS(4, "가방/지갑"),
  WATCHES(5, "시계"),
  JEWELRY(6, "주얼리"),
  FASHION_ACCESSORIES(7, "패션 액세서리"),
  ELECTRONICS_SMART_DEVICES(8, "전자기기/스마트기기"),
  LARGE_APPLIANCES(9, "대형가전"),
  SMALL_APPLIANCES(10, "소형가전"),
  SPORTS_LEISURE(11, "스포츠/레저"),
  VEHICLES_MOTORCYCLES(12, "차량/오토바이"),
  STAR_GOODS(13, "스타굿즈"),
  KIDULT(14, "키덜트"),
  ART_RARE_COLLECTIBLES(15, "예술/희귀/수집품"),
  MUSIC_INSTRUMENTS(16, "음반/악기"),
  BOOKS_TICKETS_STATIONERY(17, "도서/티켓/문구"),
  BEAUTY(18, "뷰티/미용"),
  FURNITURE_INTERIOR(19, "가구/인테리어"),
  LIFE_KITCHEN(20, "생활/주방용품"),
  TOOLS_INDUSTRIAL(21, "공구/산업용품"),
  FOOD(22, "식품"),
  BABY(23, "유아용품"),
  PET_PRODUCTS(24, "반려동물용품"),
  OTHER(25, "기타"),
  SKILL(26, "재능 (서비스나 기술 교환)");

  private final int code;
  private final String description;

  public static ProductCategory fromCode(int code) {
    for (ProductCategory productCategory : values()) {
      if (productCategory.getCode() == code) {
        return productCategory;
      }
    }
    throw new IllegalArgumentException("Invalid code: " + code);
  }
}
