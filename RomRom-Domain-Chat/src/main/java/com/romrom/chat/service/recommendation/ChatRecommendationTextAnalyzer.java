package com.romrom.chat.service.recommendation;

import com.romrom.chat.constant.ChatRecommendationKeywords;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.mongo.MessageType;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 추천 판단용 메시지 분석: 키워드 비교 정규화, 추임새 판별, 키워드 포함 여부, 최근 메시지 탐색
 */
@Component
@RequiredArgsConstructor
public class ChatRecommendationTextAnalyzer {

  // 추임새·이모티콘만으로 된 메시지 패턴
  private static final String TRIVIAL_SYMBOL_PATTERN = "[ㅋㅎㅠㅜ!?.,~]+";

  private final ChatRecommendationSettings settings;

  /**
   * 키워드 비교용 문자열 정리 (개행 > 공백, trim, 소문자, null > "")
   */
  public String normalizeKeywordSource(String content) {
    if (content == null) {
      return "";
    }
    return content.replace('\n', ' ')
        .replace('\r', ' ')
        .trim()
        .toLowerCase(Locale.ROOT);
  }

  /**
   * 추천 판단 제외 대상 여부 (빈 문자열, 추임새 목록, 키워드 없는 짧은 문장, 기호만)
   */
  public boolean isTrivialText(String text) {
    if (!StringUtils.hasText(text)) {
      return true;
    }

    String compactText = text.replace(" ", "");
    if (ChatRecommendationKeywords.TRIVIAL_TEXTS.contains(compactText)) {
      return true;
    }
    if (compactText.length() <= settings.getTrivialMessageLength()
        && !containsKeyword(compactText, ChatRecommendationKeywords.LOCATION_DIRECT_REQUEST_KEYWORDS)
        && !containsKeyword(compactText, ChatRecommendationKeywords.TRADE_COMPLETION_KEYWORDS)) {
      return true;
    }
    return compactText.matches(TRIVIAL_SYMBOL_PATTERN);
  }

  /**
   * 키워드 묶음 중 하나라도 포함 여부 (대소문자 무시)
   */
  public boolean containsKeyword(String source, List<String> keywords) {
    if (!StringUtils.hasText(source)) {
      return false;
    }
    for (String keyword : keywords) {
      if (source.contains(keyword.toLowerCase(Locale.ROOT))) {
        return true;
      }
    }
    return false;
  }

  /**
   * 마지막 메시지 (없으면 null)
   */
  public ChatMessage findLatestMessage(List<ChatMessage> recentMessages) {
    return recentMessages.isEmpty() ? null : recentMessages.get(recentMessages.size() - 1);
  }

  /**
   * 마지막 TEXT 메시지 (없으면 null)
   */
  public ChatMessage findLatestTextMessage(List<ChatMessage> recentMessages) {
    for (int index = recentMessages.size() - 1; index >= 0; index--) {
      ChatMessage recentMessage = recentMessages.get(index);
      if (recentMessage.getType() == MessageType.TEXT) {
        return recentMessage;
      }
    }
    return null;
  }

  /**
   * 마지막 의미 있는 TEXT 문장 (정규화 후, 없으면 "")
   */
  public String findLatestMeaningfulText(List<ChatMessage> recentMessages) {
    for (int index = recentMessages.size() - 1; index >= 0; index--) {
      ChatMessage recentMessage = recentMessages.get(index);
      if (recentMessage.getType() != MessageType.TEXT) {
        continue;
      }
      String normalizedText = normalizeKeywordSource(recentMessage.getContent());
      if (!isTrivialText(normalizedText)) {
        return normalizedText;
      }
    }
    return "";
  }

  /**
   * 의미 있는 TEXT 문장 스트림 (오래된 순, 정규화 후 추임새 제외, 지연 평가)
   */
  public Stream<String> streamMeaningfulTexts(List<ChatMessage> recentMessages) {
    return recentMessages.stream()
        .filter(message -> message.getType() == MessageType.TEXT)
        .map(ChatMessage::getContent)
        .map(this::normalizeKeywordSource)
        .filter(text -> !isTrivialText(text));
  }
}
