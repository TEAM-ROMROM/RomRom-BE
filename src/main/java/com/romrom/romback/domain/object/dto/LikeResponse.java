package com.romrom.romback.domain.object.dto;


import com.romrom.romback.domain.object.constant.LikeStatus;
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
