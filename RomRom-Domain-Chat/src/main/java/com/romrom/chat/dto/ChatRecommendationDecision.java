package com.romrom.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChatRecommendationDecision {
  private ChatRecommendedAction action;
  private String reason;
}
