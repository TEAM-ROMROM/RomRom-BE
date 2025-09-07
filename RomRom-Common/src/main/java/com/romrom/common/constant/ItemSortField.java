package com.romrom.common.constant;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum ItemSortField implements SortField {

  CREATED_DATE("createdDate"),

  DISTANCE("distance"),

  PREFERRED_CATEGORY("preferredCategory");

  private final String property;

  @Override
  public String getProperty() {
    return property;
  }
}
