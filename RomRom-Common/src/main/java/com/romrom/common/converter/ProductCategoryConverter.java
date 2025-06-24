package com.romrom.common.converter;

import com.romrom.common.constant.ItemCategory;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ProductCategoryConverter implements AttributeConverter<ItemCategory, Integer> {

  @Override
  public Integer convertToDatabaseColumn(ItemCategory itemCategory) {
    if (itemCategory == null) {
      return null;
    }
    return itemCategory.getCode();
  }

  @Override
  public ItemCategory convertToEntityAttribute(Integer code) {
    if (code == null) {
      return null;
    }
    return ItemCategory.fromCode(code);
  }

}
