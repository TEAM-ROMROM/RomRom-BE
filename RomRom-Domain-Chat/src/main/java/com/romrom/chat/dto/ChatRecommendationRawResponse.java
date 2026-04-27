package com.romrom.chat.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ChatRecommendationRawResponse {
  private String action;
  private String reason;
}
