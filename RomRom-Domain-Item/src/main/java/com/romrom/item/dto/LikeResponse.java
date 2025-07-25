package com.romrom.item.dto;

import com.romrom.common.constant.LikeStatus;
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
  private LikeStatus likeStatus;
}
