package com.romrom.item.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.romrom.common.constant.ItemCategory;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.web.bind.WebDataBinder;

/**
 * multipart 쉼표 값 > 희망 교환 카테고리 바인딩: 미전송 null(수정 시 유지), 빈 값 빈 목록(해제), 쉼표 구분 목록 (#862)
 */
class ItemRequestWantedCategoryBindingTest {

  @Test
  @DisplayName("미전송 > null (구버전 앱 수정 요청은 기존 값 유지)")
  void missingFieldBindsNull() {
    assertNull(bind(new MutablePropertyValues()).getWantedItemCategories());
  }

  @Test
  @DisplayName("빈 값 > 빈 목록 (전부 해제)")
  void emptyValueBindsEmptyList() {
    MutablePropertyValues propertyValues = new MutablePropertyValues();
    propertyValues.add("wantedItemCategories", "");
    assertTrue(bind(propertyValues).getWantedItemCategories().isEmpty());
  }

  @Test
  @DisplayName("쉼표 구분 > 카테고리 목록 (앱 itemTradeOptions 와 같은 형식)")
  void commaSeparatedValueBindsList() {
    MutablePropertyValues propertyValues = new MutablePropertyValues();
    propertyValues.add("wantedItemCategories", "SHOES,BEAUTY");
    assertEquals(List.of(ItemCategory.SHOES, ItemCategory.BEAUTY), bind(propertyValues).getWantedItemCategories());
  }

  private ItemRequest bind(MutablePropertyValues propertyValues) {
    ItemRequest itemRequest = new ItemRequest();
    WebDataBinder webDataBinder = new WebDataBinder(itemRequest);
    webDataBinder.setConversionService(new DefaultFormattingConversionService());
    webDataBinder.bind(propertyValues);
    return itemRequest;
  }
}
