package com.romrom.chat.service.recommendation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.romrom.chat.dto.ChatRecommendationDecision;
import com.romrom.chat.dto.ChatRecommendationRawResponse;
import com.romrom.chat.dto.ChatRecommendedAction;
import java.util.Locale;
import kr.suhsaechan.ai.model.ChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * LLM 추천 응답 해석: 응답 텍스트 추출, 코드펜스 제거, JSON > 추천 결정 변환
 */
@Component
@RequiredArgsConstructor
public class ChatRecommendationResponseParser {

  private static final String CODE_FENCE = "```";

  private final ObjectMapper objectMapper;

  /**
   * 응답 텍스트 추출 (content 우선 > message.content, 없으면 null)
   */
  public String extractContent(ChatResponse response) {
    if (response == null) {
      return null;
    }
    if (StringUtils.hasText(response.getContent())) {
      return response.getContent();
    }
    if (response.getMessage() != null) {
      return response.getMessage().getContent();
    }
    return null;
  }

  /**
   * 응답 텍스트 > 추천 결정: 코드펜스 제거 > JSON 파싱 > action enum 변환 > 이유 정리
   */
  public ChatRecommendationDecision parseDecision(String rawContent) throws JsonProcessingException {
    ChatRecommendationRawResponse rawResponse = objectMapper.readValue(stripCodeFence(rawContent), ChatRecommendationRawResponse.class);
    ChatRecommendedAction action = toAction(rawResponse.getAction());
    return new ChatRecommendationDecision(action, ChatRecommendationReasonFormatter.normalizeReason(action, rawResponse.getReason()));
  }

  // ```json ... ``` 로 감싼 응답 > 내부 JSON
  private String stripCodeFence(String rawContent) {
    String trimmedContent = rawContent.trim();
    if (!trimmedContent.startsWith(CODE_FENCE)) {
      return trimmedContent;
    }

    int firstLineBreakIndex = trimmedContent.indexOf('\n');
    if (firstLineBreakIndex < 0) {
      return trimmedContent.replace(CODE_FENCE, "").trim();
    }

    String contentWithoutOpeningFence = trimmedContent.substring(firstLineBreakIndex + 1);
    int closingFenceIndex = contentWithoutOpeningFence.lastIndexOf(CODE_FENCE);
    if (closingFenceIndex < 0) {
      return contentWithoutOpeningFence.trim();
    }
    return contentWithoutOpeningFence.substring(0, closingFenceIndex).trim();
  }

  // 문자열 action > enum (빈 값·알 수 없는 값은 NONE)
  private ChatRecommendedAction toAction(String rawAction) {
    if (!StringUtils.hasText(rawAction)) {
      return ChatRecommendedAction.NONE;
    }
    try {
      return ChatRecommendedAction.valueOf(rawAction.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return ChatRecommendedAction.NONE;
    }
  }
}
