package com.romrom.romback.global.converter;

import com.romrom.romback.domain.object.constant.ProductCategory;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ProductCategoryConverter implements AttributeConverter<ProductCategory,Integer> {


  @Override
  public Integer convertToDatabaseColumn(ProductCategory productCategory) {
    return 0;
  }

  @Override
  public ProductCategory convertToEntityAttribute(Integer integer) {
    return null;
  }
}
