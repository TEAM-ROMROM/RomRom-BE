package com.romrom.romback.global.converter;

import com.romrom.romback.domain.object.constant.ProductCategory;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ProductCategoryConverter implements AttributeConverter<ProductCategory, Integer> {

  @Override
  public Integer convertToDatabaseColumn(ProductCategory productCategory) {
    if (productCategory == null) {
      return null;
    }
    return productCategory.getCode();
  }

  @Override
  public ProductCategory convertToEntityAttribute(Integer code) {
    if (code == null) {
      return null;
    }
    return ProductCategory.fromCode(code);
  }

}
