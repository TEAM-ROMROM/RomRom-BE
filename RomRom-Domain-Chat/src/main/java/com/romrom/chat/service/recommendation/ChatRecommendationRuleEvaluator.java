package com.romrom.chat.service.recommendation;

import com.romrom.chat.constant.ChatRecommendationKeywords;
import com.romrom.chat.dto.ChatRecommendationDecision;
import com.romrom.chat.dto.ChatRecommendedAction;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.mongo.MessageType;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.chat.repository.mongo.ChatMessageRepository;
import com.romrom.common.constant.TradeStatus;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 룰 기반 추천 판단: 허용 액션 계산, 키워드·상태 기반 즉시 추천, 지연 노출 시간, LLM 호출 신호
 * 거래 상태 2종(CHATTING, TRADE_COMPLETE_REQUESTED)만 추천 대상, 그 외 상태는 허용 액션 없음
 */
@Component
@RequiredArgsConstructor
public class ChatRecommendationRuleEvaluator {

  // 교환 완료 키워드를 확인할 최근 의미 있는 문장 수
  private static final int TRADE_COMPLETION_LOOKBACK_TEXT_COUNT = 2;

  private final ChatMessageRepository chatMessageRepository;
  private final ChatRecommendationTextAnalyzer textAnalyzer;
  private final ChatRecommendationSettings settings;

  /**
   * 거래 상태·최근 메시지 기준 현재 사용자 허용 액션 후보
   */
  public EnumSet<ChatRecommendedAction> resolveAllowedActions(ChatRoom room, UUID viewerId, List<ChatMessage> recentMessages) {
    TradeStatus tradeStatus = room.getTradeRequestHistory().getTradeStatus();
    if (tradeStatus == null) {
      return EnumSet.noneOf(ChatRecommendedAction.class);
    }
    if (tradeStatus == TradeStatus.CHATTING) {
      return resolveChattingAllowedActions(room.getChatRoomId(), recentMessages);
    }
    if (tradeStatus == TradeStatus.TRADE_COMPLETE_REQUESTED) {
      return resolveTradeCompleteRequestedAllowedActions(room.getChatRoomId(), viewerId);
    }
    return EnumSet.noneOf(ChatRecommendedAction.class);
  }

  /**
   * 룰만으로 확정 가능한 추천 (확정 불가면 null > LLM 판단 대상)
   */
  public ChatRecommendationDecision resolveRuleBasedDecision(ChatRoom room, UUID viewerId, List<ChatMessage> recentMessages,
                                                            Set<ChatRecommendedAction> allowedActions) {
    TradeStatus tradeStatus = room.getTradeRequestHistory().getTradeStatus();
    if (tradeStatus == TradeStatus.TRADE_COMPLETE_REQUESTED) {
      return resolveTradeCompletionRequestedDecision(recentMessages, allowedActions);
    }

    // 마지막 메시지가 의미 있는 TEXT 가 아니면 NONE 확정
    ChatMessage latestMessage = textAnalyzer.findLatestMessage(recentMessages);
    if (latestMessage == null || latestMessage.getType() != MessageType.TEXT) {
      return new ChatRecommendationDecision(ChatRecommendedAction.NONE, null);
    }
    String latestText = textAnalyzer.normalizeKeywordSource(latestMessage.getContent());
    if (textAnalyzer.isTrivialText(latestText)) {
      return new ChatRecommendationDecision(ChatRecommendedAction.NONE, null);
    }

    // 강한 신호 순서: 위치 공유 > 교환 완료 요청
    if (allowedActions.contains(ChatRecommendedAction.SEND_LOCATION)
        && shouldRecommendLocation(viewerId, recentMessages)) {
      return toDecisionWithDefaultReason(ChatRecommendedAction.SEND_LOCATION);
    }
    if (allowedActions.contains(ChatRecommendedAction.REQUEST_TRADE_COMPLETION)
        && shouldRecommendTradeCompletion(recentMessages, latestText)) {
      return toDecisionWithDefaultReason(ChatRecommendedAction.REQUEST_TRADE_COMPLETION);
    }

    return null;
  }

  /**
   * 무응답 N초 후 노출 추천의 남은 대기 초 (교환 완료 요청 전용, 즉시 노출이면 null)
   */
  public Integer resolveDeferredRecommendationSeconds(ChatRecommendedAction action, List<ChatMessage> recentMessages) {
    if (action != ChatRecommendedAction.REQUEST_TRADE_COMPLETION) {
      return null;
    }

    ChatMessage latestMessage = textAnalyzer.findLatestMessage(recentMessages);
    if (latestMessage == null || latestMessage.getCreatedDate() == null) {
      return null;
    }

    LocalDateTime availableAt = latestMessage.getCreatedDate().plusSeconds(settings.getTradeCompletionInactivitySeconds());
    if (!availableAt.isAfter(LocalDateTime.now())) {
      return null;
    }
    return (int) Duration.between(LocalDateTime.now(), availableAt).getSeconds();
  }

  /**
   * LLM 호출 가치 여부 (마지막 메시지가 의미 있는 TEXT + 최근 대화에 추천 키워드 존재)
   */
  public boolean hasLlmRecommendationSignal(List<ChatMessage> recentMessages) {
    ChatMessage latestMessage = textAnalyzer.findLatestMessage(recentMessages);
    if (latestMessage == null || latestMessage.getType() != MessageType.TEXT) {
      return false;
    }

    String latestText = textAnalyzer.normalizeKeywordSource(latestMessage.getContent());
    if (textAnalyzer.isTrivialText(latestText)) {
      return false;
    }
    return hasRecommendationKeyword(recentMessages);
  }

  // CHATTING: 위치 공유·교환 완료 요청 (최근 위치 공유 있으면 위치 제외, 최근 취소·거절 있으면 완료 요청 제외)
  private EnumSet<ChatRecommendedAction> resolveChattingAllowedActions(UUID chatRoomId, List<ChatMessage> recentMessages) {
    EnumSet<ChatRecommendedAction> actions = EnumSet.of(
        ChatRecommendedAction.SEND_LOCATION,
        ChatRecommendedAction.REQUEST_TRADE_COMPLETION
    );
    if (containsRecentLocationMessage(recentMessages)) {
      actions.remove(ChatRecommendedAction.SEND_LOCATION);
    }
    if (shouldSuppressTradeCompletionRequest(chatRoomId)) {
      actions.remove(ChatRecommendedAction.REQUEST_TRADE_COMPLETION);
    }
    return actions;
  }

  // TRADE_COMPLETE_REQUESTED: 마지막 완료 요청 발신자 기준 요청자(취소) / 응답자(거절·확인)
  private EnumSet<ChatRecommendedAction> resolveTradeCompleteRequestedAllowedActions(UUID chatRoomId, UUID viewerId) {
    Optional<ChatMessage> latestTradeMessageOpt = chatMessageRepository
        .findFirstByChatRoomIdAndTypeInOrderByCreatedDateDesc(chatRoomId, MessageType.tradeCompletionTypes());
    if (latestTradeMessageOpt.isEmpty() || latestTradeMessageOpt.get().getType() != MessageType.TRADE_COMPLETE_REQUEST) {
      return EnumSet.noneOf(ChatRecommendedAction.class);
    }

    ChatMessage latestTradeMessage = latestTradeMessageOpt.get();
    if (viewerId.equals(latestTradeMessage.getSenderId())) {
      return EnumSet.of(ChatRecommendedAction.CANCEL_TRADE_COMPLETION_REQUEST);
    }
    return EnumSet.of(
        ChatRecommendedAction.REJECT_TRADE_COMPLETION_REQUEST,
        ChatRecommendedAction.CONFIRM_TRADE_COMPLETION
    );
  }

  // 완료 요청 진행 중 룰 판단: 취소 > 거절 키워드 > 완료 키워드
  private ChatRecommendationDecision resolveTradeCompletionRequestedDecision(List<ChatMessage> recentMessages,
                                                                            Set<ChatRecommendedAction> allowedActions) {
    if (allowedActions.contains(ChatRecommendedAction.CANCEL_TRADE_COMPLETION_REQUEST)) {
      return toDecisionWithDefaultReason(ChatRecommendedAction.CANCEL_TRADE_COMPLETION_REQUEST);
    }

    String latestMeaningfulText = textAnalyzer.findLatestMeaningfulText(recentMessages);
    if (allowedActions.contains(ChatRecommendedAction.REJECT_TRADE_COMPLETION_REQUEST)
        && textAnalyzer.containsKeyword(latestMeaningfulText, ChatRecommendationKeywords.REJECT_KEYWORDS)) {
      return toDecisionWithDefaultReason(ChatRecommendedAction.REJECT_TRADE_COMPLETION_REQUEST);
    }
    if (allowedActions.contains(ChatRecommendedAction.CONFIRM_TRADE_COMPLETION)
        && textAnalyzer.containsKeyword(latestMeaningfulText, ChatRecommendationKeywords.TRADE_COMPLETION_KEYWORDS)) {
      return toDecisionWithDefaultReason(ChatRecommendedAction.CONFIRM_TRADE_COMPLETION);
    }

    return null;
  }

  // 위치 공유 강한 신호 3종: 내 마지막 문장 확정 표현, 상대 마지막 문장 요청, 최근 상대 문장 중 요청
  private boolean shouldRecommendLocation(UUID viewerId, List<ChatMessage> recentMessages) {
    ChatMessage latestTextMessage = textAnalyzer.findLatestTextMessage(recentMessages);
    if (latestTextMessage == null) {
      return false;
    }

    String latestText = textAnalyzer.normalizeKeywordSource(latestTextMessage.getContent());
    boolean isSentByViewer = viewerId.equals(latestTextMessage.getSenderId());
    if (isSentByViewer
        && textAnalyzer.containsKeyword(latestText, ChatRecommendationKeywords.LOCATION_DIRECT_COMMIT_KEYWORDS)) {
      return true;
    }
    if (!isSentByViewer
        && textAnalyzer.containsKeyword(latestText, ChatRecommendationKeywords.LOCATION_DIRECT_REQUEST_KEYWORDS)) {
      return true;
    }

    return recentMessages.stream()
        .filter(message -> message.getType() == MessageType.TEXT)
        .filter(message -> !viewerId.equals(message.getSenderId()))
        .map(message -> textAnalyzer.normalizeKeywordSource(message.getContent()))
        .filter(text -> !textAnalyzer.isTrivialText(text))
        .anyMatch(text -> textAnalyzer.containsKeyword(text, ChatRecommendationKeywords.LOCATION_DIRECT_REQUEST_KEYWORDS));
  }

  // 교환 완료 신호: 마지막 문장 또는 최근 의미 있는 문장 2개 중 완료 키워드
  private boolean shouldRecommendTradeCompletion(List<ChatMessage> recentMessages, String latestText) {
    if (textAnalyzer.containsKeyword(latestText, ChatRecommendationKeywords.TRADE_COMPLETION_KEYWORDS)) {
      return true;
    }

    List<String> meaningfulTexts = textAnalyzer.streamMeaningfulTexts(recentMessages).toList();
    int startIndex = Math.max(0, meaningfulTexts.size() - TRADE_COMPLETION_LOOKBACK_TEXT_COUNT);
    return meaningfulTexts.subList(startIndex, meaningfulTexts.size()).stream()
        .anyMatch(text -> textAnalyzer.containsKeyword(text, ChatRecommendationKeywords.TRADE_COMPLETION_KEYWORDS));
  }

  // LLM 1차 필터 키워드 4종: 위치 요청, 위치 확정, 위치 신호, 교환 완료
  private boolean hasRecommendationKeyword(List<ChatMessage> recentMessages) {
    return textAnalyzer.streamMeaningfulTexts(recentMessages)
        .anyMatch(text ->
            textAnalyzer.containsKeyword(text, ChatRecommendationKeywords.LOCATION_DIRECT_REQUEST_KEYWORDS)
                || textAnalyzer.containsKeyword(text, ChatRecommendationKeywords.LOCATION_DIRECT_COMMIT_KEYWORDS)
                || textAnalyzer.containsKeyword(text, ChatRecommendationKeywords.LOCATION_SIGNAL_KEYWORDS)
                || textAnalyzer.containsKeyword(text, ChatRecommendationKeywords.TRADE_COMPLETION_KEYWORDS)
        );
  }

  // 최근 대화 내 위치 공유 존재 여부 (중복 추천 방지)
  private boolean containsRecentLocationMessage(List<ChatMessage> recentMessages) {
    return recentMessages.stream().anyMatch(message -> message.getType() == MessageType.LOCATION);
  }

  // 마지막 완료 관련 시스템 메시지가 취소·거절이고 재시도 쿨다운 안이면 완료 요청 재추천 보류
  private boolean shouldSuppressTradeCompletionRequest(UUID chatRoomId) {
    Optional<ChatMessage> latestTradeMessageOpt = chatMessageRepository
        .findFirstByChatRoomIdAndTypeInOrderByCreatedDateDesc(chatRoomId, MessageType.tradeCompletionTypes());
    if (latestTradeMessageOpt.isEmpty()) {
      return false;
    }

    ChatMessage latestTradeMessage = latestTradeMessageOpt.get();
    if (latestTradeMessage.getType() != MessageType.TRADE_COMPLETE_REQUEST_CANCELED
        && latestTradeMessage.getType() != MessageType.TRADE_COMPLETE_REQUEST_REJECTED) {
      return false;
    }

    LocalDateTime createdDate = latestTradeMessage.getCreatedDate();
    if (createdDate == null) {
      return false;
    }
    return createdDate.plusSeconds(settings.getTradeCompletionRetryCooldownSeconds()).isAfter(LocalDateTime.now());
  }

  private ChatRecommendationDecision toDecisionWithDefaultReason(ChatRecommendedAction action) {
    return new ChatRecommendationDecision(action, ChatRecommendationReasonFormatter.resolveDefaultReason(action));
  }
}
