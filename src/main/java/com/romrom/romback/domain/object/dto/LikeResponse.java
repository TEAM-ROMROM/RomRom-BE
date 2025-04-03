package com.romrom.romback.domain.object.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@ToString
@AllArgsConstructor
@Getter
@Setter
@Builder
@NoArgsConstructor
public class LikeResponse {
  private Integer likeCount;
  private LikeStatusEnum likeStatusEnum;

  public enum LikeStatusEnum {
    LIKE,
    UNLIKE
  }
}
