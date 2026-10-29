package com.romrom.chat.service;

import com.romrom.chat.dto.ChatActionRecommendationPayload;
import com.romrom.chat.dto.ChatRecommendationDecision;
import com.romrom.chat.dto.ChatRecommendedAction;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.chat.event.ChatRecommendationRequestedEvent;
import com.romrom.chat.repository.mongo.ChatMessageRepository;
import com.romrom.chat.repository.postgres.ChatRoomRepository;
import com.romrom.chat.service.recommendation.ChatRecommendationCooldownStore;
import com.romrom.chat.service.recommendation.ChatRecommendationPromptBuilder;
import com.romrom.chat.service.recommendation.ChatRecommendationReasonFormatter;
import com.romrom.chat.service.recommendation.ChatRecommendationResponseParser;
import com.romrom.chat.service.recommendation.ChatRecommendationRuleEvaluator;
import com.romrom.chat.service.recommendation.ChatRecommendationSettings;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kr.suhsaechan.ai.model.ChatRequest;
import kr.suhsaechan.ai.model.ChatResponse;
import kr.suhsaechan.ai.service.SuhAiderEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 채팅 다음 행동 추천: messages/get 응답용 추천 계산, 새 메시지 후 실시간 추천 전송
 * 캐시 확인 > 허용 액션 계산 > 룰 우선 추천 > 필요 시 LLM > 결과 캐시
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatActionRecommendationService {

  private final ChatRoomRepository chatRoomRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final ChatWebSocketService chatWebSocketService;
  private final SuhAiderEngine suhAiderEngine;
  private final ChatRecommendationSettings settings;
  private final ChatRecommendationRuleEvaluator ruleEvaluator;
  private final ChatRecommendationPromptBuilder promptBuilder;
  private final ChatRecommendationResponseParser responseParser;
  private final ChatRecommendationCooldownStore cooldownStore;

  /**
   * 현재 사용자 추천 계산: 활성화 확인 > 최근 메시지 정규화 > 캐시 재사용 > 추천 판단·캐시 (비활성 시 null)
   */
  @Transactional(readOnly = true)
  public ChatActionRecommendationPayload recommendForViewer(ChatRoom room, UUID viewerId, List<ChatMessage> recentMessages) {
    if (!settings.isRecommendationEnabled()) {
      return null;
    }

    List<ChatMessage> normalizedMessages = normalizeRecentMessages(room.getChatRoomId(), recentMessages);
    String basedOnMessageId = findLatestMessageId(normalizedMessages);

    // 같은 마지막 메시지 기준 추천 재사용 (messages/get 반복 호출 비용 절감)
    ChatActionRecommendationPayload cachedRecommendation =
        cooldownStore.getCachedRecommendation(room.getChatRoomId(), viewerId, basedOnMessageId);
    if (cachedRecommendation != null) {
      return cachedRecommendation;
    }

    return computeRecommendation(room, viewerId, normalizedMessages, basedOnMessageId);
  }

  /**
   * 새 메시지 후 실시간 추천 전송: 방 조회 > 최근 메시지 조회 > 보낸 사람 전송 > 방 안의 상대방 전송
   */
  @Transactional(readOnly = true)
  public void dispatchRealtimeRecommendations(ChatRecommendationRequestedEvent event) {
    if (!settings.isRecommendationEnabled()) {
      return;
    }

    ChatRoom room = chatRoomRepository.findByChatRoomIdWithSenderAndReceiver(event.chatRoomId())
        .orElse(null);
    if (room == null) {
      log.warn("채팅 추천 대상 방을 찾을 수 없습니다. roomId={}", event.chatRoomId());
      return;
    }

    List<ChatMessage> recentMessages = normalizeRecentMessages(room.getChatRoomId(), null);

    // 보낸 사람은 항상, 상대방은 방 안에 있을 때만 실시간 수신
    sendRecommendationToMember(room, event.senderId(), recentMessages);
    if (event.recipientPresent()) {
      sendRecommendationToMember(room, event.recipientId(), recentMessages);
    }
  }

  // 추천 판단 > 결과 캐시: 허용 액션 없음 > 룰 확정 > LLM 신호 없음 > LLM 판단 순
  private ChatActionRecommendationPayload computeRecommendation(ChatRoom room, UUID viewerId,
                                                                List<ChatMessage> normalizedMessages, String basedOnMessageId) {
    UUID chatRoomId = room.getChatRoomId();

    // 거래 상태상 불가능한 액션 선제거
    EnumSet<ChatRecommendedAction> allowedActions = ruleEvaluator.resolveAllowedActions(room, viewerId, normalizedMessages);
    if (allowedActions.isEmpty()) {
      return cacheNoneRecommendation(chatRoomId, viewerId, basedOnMessageId);
    }

    // 키워드·상태만으로 확정 가능하면 LLM 생략
    ChatRecommendationDecision ruleBasedDecision =
        ruleEvaluator.resolveRuleBasedDecision(room, viewerId, normalizedMessages, allowedActions);
    if (ruleBasedDecision != null) {
      return cacheDecidedRecommendation(chatRoomId, viewerId, basedOnMessageId, ruleBasedDecision, normalizedMessages);
    }

    // 신호가 약하거나 LLM 쿨다운 중이면 호출 생략
    if (!shouldAttemptLlm(chatRoomId, viewerId, normalizedMessages)) {
      return cacheNoneRecommendation(chatRoomId, viewerId, basedOnMessageId);
    }

    ChatRecommendationDecision llmDecision = requestRecommendation(room, viewerId, normalizedMessages, allowedActions);
    if (llmDecision == null || llmDecision.getAction() == null || !isAllowed(llmDecision.getAction(), allowedActions)) {
      return cacheNoneRecommendation(chatRoomId, viewerId, basedOnMessageId);
    }
    return cacheDecidedRecommendation(chatRoomId, viewerId, basedOnMessageId, llmDecision, normalizedMessages);
  }

  // 확정된 추천 캐시: 지연 노출 대기 중 > 남은 초만큼 NONE, 같은 액션 쿨다운 > NONE, 그 외 추천 payload
  private ChatActionRecommendationPayload cacheDecidedRecommendation(UUID chatRoomId, UUID viewerId, String basedOnMessageId,
                                                                     ChatRecommendationDecision decision,
                                                                     List<ChatMessage> normalizedMessages) {
    Integer deferredSeconds = ruleEvaluator.resolveDeferredRecommendationSeconds(decision.getAction(), normalizedMessages);
    if (deferredSeconds != null) {
      return cooldownStore.cacheRecommendation(
          ChatActionRecommendationPayload.none(chatRoomId, viewerId, basedOnMessageId),
          deferredSeconds
      );
    }
    if (!cooldownStore.tryAcquireSameActionCooldown(chatRoomId, viewerId, decision.getAction())) {
      return cacheNoneRecommendation(chatRoomId, viewerId, basedOnMessageId);
    }
    return cooldownStore.cacheRecommendation(
        buildPayload(chatRoomId, viewerId, basedOnMessageId, decision.getAction(), decision.getReason())
    );
  }

  private ChatActionRecommendationPayload cacheNoneRecommendation(UUID chatRoomId, UUID viewerId, String basedOnMessageId) {
    return cooldownStore.cacheRecommendation(ChatActionRecommendationPayload.none(chatRoomId, viewerId, basedOnMessageId));
  }

  // 특정 사용자 1명 추천 계산 > 사용자 전용 웹소켓 채널 전송
  private void sendRecommendationToMember(ChatRoom room, UUID memberId, List<ChatMessage> recentMessages) {
    String targetEmail = resolveMemberEmail(room, memberId);
    if (targetEmail == null) {
      log.warn("채팅 추천 대상 회원 이메일을 찾을 수 없습니다. roomId={}, memberId={}", room.getChatRoomId(), memberId);
      return;
    }

    ChatActionRecommendationPayload payload = recommendForViewer(room, memberId, recentMessages);
    if (payload == null) {
      return;
    }
    chatWebSocketService.sendRecommendationEvent(targetEmail, payload);
  }

  // LLM 추천 요청: 호출 쿨다운 점유 > 요청 조립 > 호출 > 응답 해석 (실패 시 null)
  private ChatRecommendationDecision requestRecommendation(ChatRoom room, UUID viewerId, List<ChatMessage> recentMessages,
                                                           Set<ChatRecommendedAction> allowedActions) {
    try {
      // 같은 방·사용자 연속 LLM 호출 잠금 (비동기 스레드 몰림 방지)
      if (!cooldownStore.tryAcquireLlmCooldown(room.getChatRoomId(), viewerId)) {
        return null;
      }

      ChatRequest request = promptBuilder.buildChatRequest(room, viewerId, recentMessages, allowedActions);
      ChatResponse response = suhAiderEngine.chat(request);
      String rawContent = responseParser.extractContent(response);
      if (!StringUtils.hasText(rawContent)) {
        log.warn("채팅 추천 LLM 응답이 비어있습니다. roomId={}, viewerId={}", room.getChatRoomId(), viewerId);
        return null;
      }
      return responseParser.parseDecision(rawContent);
    } catch (Exception e) {
      log.warn("채팅 추천 LLM 호출 실패. roomId={}, viewerId={}, error={}", room.getChatRoomId(), viewerId, e.getMessage());
      return null;
    }
  }

  // LLM 호출 조건: 추천 신호 존재 + 같은 사용자 LLM 쿨다운 아님
  private boolean shouldAttemptLlm(UUID chatRoomId, UUID viewerId, List<ChatMessage> recentMessages) {
    return ruleEvaluator.hasLlmRecommendationSignal(recentMessages)
        && !cooldownStore.isLlmCooldownActive(chatRoomId, viewerId);
  }

  // 최근 메시지 정규화: 미전달 시 DB 조회 > 오래된 순 정렬 > 최근 N건 제한
  private List<ChatMessage> normalizeRecentMessages(UUID chatRoomId, List<ChatMessage> recentMessages) {
    int recentMessageLimit = settings.getRecentMessageLimit();
    List<ChatMessage> messages = recentMessages;
    if (messages == null || messages.isEmpty()) {
      messages = chatMessageRepository.findByChatRoomIdOrderByCreatedDateDesc(
          chatRoomId,
          PageRequest.of(0, recentMessageLimit, Sort.by(Sort.Direction.DESC, "createdDate"))
      ).getContent();
    }

    List<ChatMessage> sortedMessages = new ArrayList<>(messages);
    sortedMessages.sort((left, right) -> left.getCreatedDate().compareTo(right.getCreatedDate()));
    if (sortedMessages.size() > recentMessageLimit) {
      return new ArrayList<>(sortedMessages.subList(sortedMessages.size() - recentMessageLimit, sortedMessages.size()));
    }
    return sortedMessages;
  }

  // 추천 기준 메시지 ID: 마지막 메시지 ID (없으면 null)
  private String findLatestMessageId(List<ChatMessage> normalizedMessages) {
    return normalizedMessages.isEmpty() ? null : normalizedMessages.get(normalizedMessages.size() - 1).getChatMessageId();
  }

  // LLM 반환 action 최종 검증 (NONE 또는 허용 집합 포함)
  private boolean isAllowed(ChatRecommendedAction action, Set<ChatRecommendedAction> allowedActions) {
    return action == ChatRecommendedAction.NONE || allowedActions.contains(action);
  }

  // 채팅방 참여자 UUID > 웹소켓 전송용 이메일 (참여자 아니면 null)
  private String resolveMemberEmail(ChatRoom room, UUID memberId) {
    if (room.getTradeReceiver().getMemberId().equals(memberId)) {
      return room.getTradeReceiver().getEmail();
    }
    if (room.getTradeSender().getMemberId().equals(memberId)) {
      return room.getTradeSender().getEmail();
    }
    return null;
  }

  // 추천 payload 생성 (NONE·null 액션은 none payload)
  private ChatActionRecommendationPayload buildPayload(UUID chatRoomId, UUID viewerId, String basedOnMessageId,
                                                       ChatRecommendedAction action, String reason) {
    if (action == null || action.isNone()) {
      return ChatActionRecommendationPayload.none(chatRoomId, viewerId, basedOnMessageId);
    }

    return ChatActionRecommendationPayload.builder()
        .chatRoomId(chatRoomId)
        .targetMemberId(viewerId)
        .action(action)
        .reason(ChatRecommendationReasonFormatter.normalizeReason(action, reason))
        .basedOnMessageId(basedOnMessageId)
        .createdDate(LocalDateTime.now())
        .build();
  }
}
