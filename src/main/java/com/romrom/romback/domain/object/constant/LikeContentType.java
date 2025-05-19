package com.romrom.romback.domain.object.constant;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum LikeContentType {
  ITEM("아이템 좋아요");

  private final String description;
}
