package com.romrom.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.romrom.chat.constant.ChatRecommendationConstants;
import com.romrom.chat.constant.ChatRecommendationKeywords;
import com.romrom.chat.dto.ChatActionRecommendationPayload;
import com.romrom.chat.dto.ChatRecommendedAction;
import com.romrom.chat.dto.ChatRecommendationDecision;
import com.romrom.chat.dto.ChatRecommendationRawResponse;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.mongo.MessageType;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.chat.event.ChatRecommendationRequestedEvent;
import com.romrom.chat.properties.ChatRecommendationPolicyProperties;
import com.romrom.chat.properties.ChatRecommendationPromptProperties;
import com.romrom.chat.repository.mongo.ChatMessageRepository;
import com.romrom.chat.repository.postgres.ChatRoomRepository;
import com.romrom.common.constant.TradeStatus;
import com.romrom.common.service.SystemConfigCacheService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import kr.suhsaechan.ai.model.ChatRequest;
import kr.suhsaechan.ai.model.ChatResponse;
import kr.suhsaechan.ai.service.SuhAiderEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
// 최근 대화와 거래 상태를 보고 현재 사용자에게 보여줄 다음 행동 추천을 계산한다.
// 판단 순서는 "캐시 확인 -> 허용 액션 계산 -> 룰 우선 추천 -> 필요 시 LLM -> 결과 캐시" 이다.
public class ChatActionRecommendationService {

  // 채팅 추천 프롬프트 관련 system_config 키 (관리자 페이지에서 런타임 수정 가능)
  private static final String CHAT_RECOMMENDATION_INSTRUCTION_CONFIG_KEY = "ai.chat.recommendation.prompt.instruction";
  private static final String CHAT_RECOMMENDATION_ENABLED_CONFIG_KEY = "ai.chat.recommendation.prompt.enabled";

  private final ChatRoomRepository chatRoomRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final ChatWebSocketService chatWebSocketService;
  private final SuhAiderEngine suhAiderEngine;
  private final SystemConfigCacheService systemConfigCacheService;
  private final ChatRecommendationPolicyProperties policyProperties;
  private final ChatRecommendationPromptProperties promptProperties;
  private final ObjectMapper objectMapper;
  private final RedisTemplate<String, Object> redisTemplate;

  // messages/get 응답에서 현재 사용자가 볼 추천 액션을 계산한다.
  @Transactional(readOnly = true)
  public ChatActionRecommendationPayload recommendForViewer(ChatRoom room, UUID viewerId, List<ChatMessage> recentMessages) {
    if (!isRecommendationEnabled()) {
      return null;
    }

    List<ChatMessage> normalizedMessages = normalizeRecentMessages(room.getChatRoomId(), recentMessages);
    String basedOnMessageId = normalizedMessages.isEmpty()
        ? null : normalizedMessages.get(normalizedMessages.size() - 1).getChatMessageId();

    // 같은 마지막 메시지 기준 추천은 재사용해서 messages/get 반복 호출 비용을 줄인다.
    ChatActionRecommendationPayload cachedRecommendation = getCachedRecommendation(room.getChatRoomId(), viewerId, basedOnMessageId);
    if (cachedRecommendation != null) {
      return cachedRecommendation;
    }

    // 거래 상태상 불가능한 액션은 여기서 먼저 제거한다.
    EnumSet<ChatRecommendedAction> allowedActions = resolveAllowedActions(room, viewerId, normalizedMessages);
    if (allowedActions.isEmpty()) {
      return cacheRecommendation(ChatActionRecommendationPayload.none(room.getChatRoomId(), viewerId, basedOnMessageId));
    }

    // 키워드나 상태만으로 충분히 확정 가능한 경우는 LLM 없이 바로 추천한다.
    ChatRecommendationDecision ruleBasedDecision = resolveRuleBasedDecision(room, viewerId, normalizedMessages, allowedActions);
    if (ruleBasedDecision != null) {
      // 교환 완료 요청처럼 "무응답 N초 후" 조건이 있는 추천은 그때까지 NONE 을 캐시한다.
      Integer deferredSeconds = resolveDeferredRecommendationSeconds(ruleBasedDecision.getAction(), normalizedMessages);
      if (deferredSeconds != null) {
        return cacheRecommendation(
            ChatActionRecommendationPayload.none(room.getChatRoomId(), viewerId, basedOnMessageId),
            deferredSeconds
        );
      }
      if (isSameActionOnCooldown(room.getChatRoomId(), viewerId, ruleBasedDecision.getAction())) {
        return cacheRecommendation(ChatActionRecommendationPayload.none(room.getChatRoomId(), viewerId, basedOnMessageId));
      }
      return cacheRecommendation(buildPayload(
          room.getChatRoomId(),
          viewerId,
          basedOnMessageId,
          ruleBasedDecision.getAction(),
          ruleBasedDecision.getReason()
      ));
    }

    // 신호가 약하거나 쿨다운 중이면 LLM 호출 자체를 건너뛴다.
    if (!shouldAttemptLlm(room.getChatRoomId(), viewerId, normalizedMessages)) {
      return cacheRecommendation(ChatActionRecommendationPayload.none(room.getChatRoomId(), viewerId, basedOnMessageId));
    }

    // 여기까지 왔을 때만 실제 모델을 호출한다.
    ChatRecommendationDecision decision = requestRecommendation(room, viewerId, normalizedMessages, allowedActions);
    if (decision == null || decision.getAction() == null || !isAllowed(decision.getAction(), allowedActions)) {
      return cacheRecommendation(ChatActionRecommendationPayload.none(room.getChatRoomId(), viewerId, basedOnMessageId));
    }

    Integer deferredSeconds = resolveDeferredRecommendationSeconds(decision.getAction(), normalizedMessages);
    if (deferredSeconds != null) {
      return cacheRecommendation(
          ChatActionRecommendationPayload.none(room.getChatRoomId(), viewerId, basedOnMessageId),
          deferredSeconds
      );
    }

    if (isSameActionOnCooldown(room.getChatRoomId(), viewerId, decision.getAction())) {
      return cacheRecommendation(ChatActionRecommendationPayload.none(room.getChatRoomId(), viewerId, basedOnMessageId));
    }

    return cacheRecommendation(buildPayload(room.getChatRoomId(), viewerId, basedOnMessageId, decision.getAction(), decision.getReason()));
  }

  // 새 메시지 저장 후 추천이 필요한 사용자에게 실시간 추천 이벤트를 보낸다.
  @Transactional(readOnly = true)
  public void dispatchRealtimeRecommendations(ChatRecommendationRequestedEvent event) {
    if (!isRecommendationEnabled()) {
      return;
    }

    ChatRoom room = chatRoomRepository.findByChatRoomIdWithSenderAndReceiver(event.chatRoomId())
        .orElse(null);
    if (room == null) {
      log.warn("채팅 추천 대상 방을 찾을 수 없습니다. roomId={}", event.chatRoomId());
      return;
    }

    List<ChatMessage> recentMessages = normalizeRecentMessages(room.getChatRoomId(), null);

    // 보낸 사람은 항상 자기 기준 추천을 받고, 상대방은 방 안에 있을 때만 실시간으로 받는다.
    sendRecommendationToMember(room, event.senderId(), recentMessages);
    if (event.recipientPresent()) {
      sendRecommendationToMember(room, event.recipientId(), recentMessages);
    }
  }

  // ----------------- private methods -----------------

  // 특정 사용자 1명에게 추천을 계산해 사용자 전용 웹소켓 채널로 전송한다.
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

  // 허용된 액션 집합 안에서만 LLM이 JSON 응답을 반환하도록 요청한다.
  private ChatRecommendationDecision requestRecommendation(ChatRoom room, UUID viewerId, List<ChatMessage> recentMessages,
                                                           Set<ChatRecommendedAction> allowedActions) {
    try {
      // 같은 사용자/방에 대한 연속 LLM 호출을 짧게 잠가서 비동기 스레드가 몰리지 않게 한다.
      if (!acquireLlmCooldown(room.getChatRoomId(), viewerId)) {
        return null;
      }

      String model = resolveRecommendationModel();
      String userPrompt = buildUserPrompt(room, viewerId, recentMessages, allowedActions);

      Map<String, Object> options = new HashMap<>();
      options.put("temperature", getSafeTemperature());
      options.put("num_predict", getSafeMaxOutputTokens());

      // 관리자 페이지에서 수정 가능한 런타임 instruction 을 우선 사용, 비어 있으면 yml 기본값으로 fallback
      String resolvedInstruction = resolveRecommendationInstruction();

      ChatRequest request = ChatRequest.builder()
          .model(model)
          .messages(List.of(
              kr.suhsaechan.ai.model.ChatMessage.system(resolvedInstruction),
              kr.suhsaechan.ai.model.ChatMessage.user(userPrompt)
          ))
          .stream(false)
          .options(options)
          .responseSchema(ChatRecommendationConstants.RESPONSE_SCHEMA)
          .build();

      ChatResponse response = suhAiderEngine.chat(request);
      String rawContent = extractContent(response);
      if (isBlank(rawContent)) {
        log.warn("채팅 추천 LLM 응답이 비어있습니다. roomId={}, viewerId={}", room.getChatRoomId(), viewerId);
        return null;
      }

      ChatRecommendationRawResponse rawResponse = objectMapper.readValue(stripCodeFence(rawContent), ChatRecommendationRawResponse.class);
      ChatRecommendedAction action = toAction(rawResponse.getAction());
      return new ChatRecommendationDecision(action, normalizeReason(action, rawResponse.getReason()));
    } catch (Exception e) {
      log.warn("채팅 추천 LLM 호출 실패. roomId={}, viewerId={}, error={}", room.getChatRoomId(), viewerId, e.getMessage());
      return null;
    }
  }

  // 거래 상태와 최근 메시지를 바탕으로 현재 사용자에게 허용할 추천 액션 후보를 고른다.
  private EnumSet<ChatRecommendedAction> resolveAllowedActions(ChatRoom room, UUID viewerId, List<ChatMessage> recentMessages) {
    TradeStatus tradeStatus = room.getTradeRequestHistory().getTradeStatus();
    if (tradeStatus == null) {
      return EnumSet.noneOf(ChatRecommendedAction.class);
    }

    if (tradeStatus == TradeStatus.CHATTING) {
      EnumSet<ChatRecommendedAction> actions = EnumSet.of(
          ChatRecommendedAction.SEND_LOCATION,
          ChatRecommendedAction.REQUEST_TRADE_COMPLETION
      );
      if (containsRecentLocationMessage(recentMessages)) {
        actions.remove(ChatRecommendedAction.SEND_LOCATION);
      }
      if (shouldSuppressTradeCompletionRequest(room.getChatRoomId())) {
        actions.remove(ChatRecommendedAction.REQUEST_TRADE_COMPLETION);
      }
      return actions;
    }

    if (tradeStatus == TradeStatus.TRADE_COMPLETE_REQUESTED) {
      // 마지막 거래 시스템 메시지의 발신자를 기준으로 요청자/응답자 행동을 나눈다.
      Optional<ChatMessage> latestTradeMessageOpt = chatMessageRepository
          .findFirstByChatRoomIdAndTypeInOrderByCreatedDateDesc(room.getChatRoomId(), MessageType.tradeCompletionTypes());
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

    return EnumSet.noneOf(ChatRecommendedAction.class);
  }

  // 룰만으로 바로 추천할 수 있는 경우를 먼저 처리해 LLM 호출을 최대한 줄인다.
  private ChatRecommendationDecision resolveRuleBasedDecision(ChatRoom room, UUID viewerId, List<ChatMessage> recentMessages,
                                                             Set<ChatRecommendedAction> allowedActions) {
    TradeStatus tradeStatus = room.getTradeRequestHistory().getTradeStatus();
    if (tradeStatus == TradeStatus.TRADE_COMPLETE_REQUESTED) {
      return resolveTradeCompletionRequestedDecision(recentMessages, allowedActions);
    }

    ChatMessage latestMessage = recentMessages.isEmpty() ? null : recentMessages.get(recentMessages.size() - 1);
    if (latestMessage == null || latestMessage.getType() != MessageType.TEXT) {
      return new ChatRecommendationDecision(ChatRecommendedAction.NONE, null);
    }

    String latestText = normalizeKeywordSource(latestMessage.getContent());
    if (isTrivialText(latestText)) {
      return new ChatRecommendationDecision(ChatRecommendedAction.NONE, null);
    }

    if (allowedActions.contains(ChatRecommendedAction.SEND_LOCATION)
        && shouldRecommendLocation(viewerId, recentMessages)) {
      return new ChatRecommendationDecision(
          ChatRecommendedAction.SEND_LOCATION,
          defaultReason(ChatRecommendedAction.SEND_LOCATION)
      );
    }

    if (allowedActions.contains(ChatRecommendedAction.REQUEST_TRADE_COMPLETION)
        && shouldRecommendTradeCompletion(recentMessages, latestText)) {
      return new ChatRecommendationDecision(
          ChatRecommendedAction.REQUEST_TRADE_COMPLETION,
          defaultReason(ChatRecommendedAction.REQUEST_TRADE_COMPLETION)
      );
    }

    return null;
  }

  // 추천 판단에 쓸 최근 메시지를 조회하고 오래된 순으로 정렬해 정규화한다.
  private List<ChatMessage> normalizeRecentMessages(UUID chatRoomId, List<ChatMessage> recentMessages) {
    int recentMessageLimit = getSafeRecentMessageLimit();
    List<ChatMessage> messages = recentMessages;
    if (messages == null || messages.isEmpty()) {
      messages = chatMessageRepository.findByChatRoomIdOrderByCreatedDateDesc(
          chatRoomId,
          PageRequest.of(0, recentMessageLimit, Sort.by(Sort.Direction.DESC, "createdDate"))
      ).getContent();
    }

    List<ChatMessage> sorted = new ArrayList<>(messages);
    sorted.sort((left, right) -> left.getCreatedDate().compareTo(right.getCreatedDate()));
    if (sorted.size() > recentMessageLimit) {
      return new ArrayList<>(sorted.subList(sorted.size() - recentMessageLimit, sorted.size()));
    }
    return sorted;
  }

  // 최근 대화 안에 위치 공유가 이미 있었는지 확인해 중복 추천을 줄인다.
  private boolean containsRecentLocationMessage(List<ChatMessage> recentMessages) {
    return recentMessages.stream().anyMatch(message -> message.getType() == MessageType.LOCATION);
  }

  // 현재 사용자 관점의 대화 요약과 허용 액션 목록을 LLM 입력 프롬프트로 조립한다.
  private String buildUserPrompt(ChatRoom room, UUID viewerId, List<ChatMessage> recentMessages,
                                 Set<ChatRecommendedAction> allowedActions) {
    StringBuilder prompt = new StringBuilder();
    prompt.append("trade_status=").append(room.getTradeRequestHistory().getTradeStatus()).append('\n');
    prompt.append("allowed_actions=").append(String.join(", ", allowedActions.stream().map(Enum::name).toList())).append('\n');
    prompt.append("allowed_action_descriptions=").append(buildAllowedActionDescriptions(allowedActions)).append('\n');
    prompt.append("recent_messages:\n");

    for (ChatMessage recentMessage : recentMessages.stream().filter(this::isPromptRelevantMessage).toList()) {
      String role = viewerId.equals(recentMessage.getSenderId()) ? "SELF" : "OPPONENT";
      prompt.append("- ")
          .append(role)
          .append(" [")
          .append(recentMessage.getType())
          .append("] ");

      if (recentMessage.getType() == MessageType.LOCATION) {
        prompt.append("location shared");
      } else if (recentMessage.getType() == MessageType.IMAGE) {
        prompt.append(normalizeMessageContent(recentMessage.getContent(), "image shared"));
      } else {
        prompt.append(normalizeMessageContent(recentMessage.getContent(), ""));
      }

      prompt.append('\n');
    }
    prompt.append("return_json_only=true");
    return prompt.toString();
  }

  // 프롬프트 안에 허용 액션 설명을 붙여 LLM이 enum 의미를 안정적으로 이해하게 한다.
  private String buildAllowedActionDescriptions(Set<ChatRecommendedAction> allowedActions) {
    List<String> descriptions = new ArrayList<>();
    for (ChatRecommendedAction allowedAction : allowedActions) {
      descriptions.add(switch (allowedAction) {
        case SEND_LOCATION -> "SEND_LOCATION: share meeting location";
        case REQUEST_TRADE_COMPLETION -> "REQUEST_TRADE_COMPLETION: request trade completion";
        case CANCEL_TRADE_COMPLETION_REQUEST -> "CANCEL_TRADE_COMPLETION_REQUEST: cancel my completion request";
        case REJECT_TRADE_COMPLETION_REQUEST -> "REJECT_TRADE_COMPLETION_REQUEST: reject opponent request";
        case CONFIRM_TRADE_COMPLETION -> "CONFIRM_TRADE_COMPLETION: confirm opponent request";
        case NONE -> "NONE: no recommendation";
      });
    }
    descriptions.add("NONE: choose when no action is clearly needed");
    return String.join(" | ", descriptions);
  }

  // 너무 긴 메시지나 개행을 잘라 프롬프트 길이를 일정하게 유지한다.
  private String normalizeMessageContent(String content, String fallback) {
    String normalized = isBlank(content) ? fallback : content.replace('\n', ' ').replace('\r', ' ').trim();
    if (normalized == null) {
      return "";
    }
    int maxPromptMessageLength = getSafeMaxPromptMessageLength();
    if (normalized.length() > maxPromptMessageLength) {
      return normalized.substring(0, maxPromptMessageLength);
    }
    return normalized;
  }

  // LLM이 반환한 action 이 실제 허용 집합 안에 있는지 최종 검증한다.
  private boolean isAllowed(ChatRecommendedAction action, Set<ChatRecommendedAction> allowedActions) {
    return action == ChatRecommendedAction.NONE || allowedActions.contains(action);
  }

  // 문자열 action 값을 서버 enum 으로 안전하게 변환한다.
  private ChatRecommendedAction toAction(String rawAction) {
    if (isBlank(rawAction)) {
      return ChatRecommendedAction.NONE;
    }
    try {
      return ChatRecommendedAction.valueOf(rawAction.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return ChatRecommendedAction.NONE;
    }
  }

  // 추천 이유 문장을 한 줄 짧은 문장으로 정리해 응답 payload 에 맞춘다.
  private String normalizeReason(ChatRecommendedAction action, String reason) {
    if (isBlank(reason)) {
      return defaultReason(action);
    }
    String normalized = reason.replace('\n', ' ').replace('\r', ' ').trim();
    return normalized.length() > 160 ? normalized.substring(0, 160) : normalized;
  }

  // 라이브러리 응답 형태 차이를 흡수해 실제 텍스트 응답만 추출한다.
  private String extractContent(ChatResponse response) {
    if (response == null) {
      return null;
    }
    if (!isBlank(response.getContent())) {
      return response.getContent();
    }
    if (response.getMessage() != null) {
      return response.getMessage().getContent();
    }
    return null;
  }

  // 모델이 ```json 코드펜스로 감싸 반환해도 JSON 파싱이 가능하도록 벗겨낸다.
  private String stripCodeFence(String rawContent) {
    String trimmed = rawContent.trim();
    if (!trimmed.startsWith("```")) {
      return trimmed;
    }

    int firstLineBreak = trimmed.indexOf('\n');
    if (firstLineBreak < 0) {
      return trimmed.replace("```", "").trim();
    }

    String withoutFirstFence = trimmed.substring(firstLineBreak + 1);
    int closingFence = withoutFirstFence.lastIndexOf("```");
    if (closingFence < 0) {
      return withoutFirstFence.trim();
    }
    return withoutFirstFence.substring(0, closingFence).trim();
  }

  // 채팅방 참여자 UUID 로 사용자 전용 웹소켓 전송에 필요한 이메일을 찾는다.
  private String resolveMemberEmail(ChatRoom room, UUID memberId) {
    if (room.getTradeReceiver().getMemberId().equals(memberId)) {
      return room.getTradeReceiver().getEmail();
    }
    if (room.getTradeSender().getMemberId().equals(memberId)) {
      return room.getTradeSender().getEmail();
    }
    return null;
  }

  // 프롬프트 설정이 실제로 주입됐을 때만 추천 기능을 활성화한다.
  // enabled / instruction 모두 system_config(Redis 캐시) 우선, 없으면 yml fallback 으로 판정한다.
  private boolean isRecommendationEnabled() {
    return resolveRecommendationEnabled() && !isBlank(resolveRecommendationInstruction());
  }

  // 관리자 페이지에서 지정한 instruction 이 있으면 사용, 없거나 비어 있으면 yml 기본값으로 fallback
  private String resolveRecommendationInstruction() {
    String overriddenInstruction = systemConfigCacheService.get(CHAT_RECOMMENDATION_INSTRUCTION_CONFIG_KEY);
    if (overriddenInstruction != null && !overriddenInstruction.isBlank()) {
      return overriddenInstruction;
    }
    return promptProperties.getInstruction();
  }

  // 관리자 페이지의 활성화 토글이 우선이고, 값이 없으면 yml 기본값(enabled)으로 fallback
  private boolean resolveRecommendationEnabled() {
    String overriddenEnabled = systemConfigCacheService.get(CHAT_RECOMMENDATION_ENABLED_CONFIG_KEY);
    if (overriddenEnabled == null || overriddenEnabled.isBlank()) {
      return promptProperties.isEnabled();
    }
    return Boolean.parseBoolean(overriddenEnabled.trim());
  }

  // 채팅 추천 전용 모델명을 우선 적용하고, 없으면 전용 시스템 설정값이나 기본값을 사용한다.
  private String resolveRecommendationModel() {
    if (!isBlank(promptProperties.getModel())) {
      return promptProperties.getModel();
    }

    String configuredModel = systemConfigCacheService.get("ai.chat.recommendation.model");
    if (!isBlank(configuredModel)) {
      return configuredModel;
    }

    return ChatRecommendationConstants.DEFAULT_CHAT_MODEL;
  }

  // 최신 메시지 기준 추천 결과를 잠시 캐시해 방 재진입이나 중복 조회 시 재계산을 줄인다.
  private ChatActionRecommendationPayload getCachedRecommendation(UUID chatRoomId, UUID viewerId, String basedOnMessageId) {
    Object cachedValue = redisTemplate.opsForValue().get(buildCacheKey(chatRoomId, viewerId));
    if (cachedValue == null) {
      return null;
    }

    ChatActionRecommendationPayload cachedPayload = cachedValue instanceof ChatActionRecommendationPayload payload
        ? payload
        : objectMapper.convertValue(cachedValue, ChatActionRecommendationPayload.class);

    if (cachedPayload == null) {
      return null;
    }
    if (!chatRoomId.equals(cachedPayload.getChatRoomId()) || !viewerId.equals(cachedPayload.getTargetMemberId())) {
      return null;
    }
    if (basedOnMessageId == null) {
      return cachedPayload.getBasedOnMessageId() == null ? cachedPayload : null;
    }
    return basedOnMessageId.equals(cachedPayload.getBasedOnMessageId()) ? cachedPayload : null;
  }

  // 최신 추천 결과를 Redis에 저장해 같은 메시지 기준 재호출을 피한다.
  private ChatActionRecommendationPayload cacheRecommendation(ChatActionRecommendationPayload payload) {
    return cacheRecommendation(payload, getCacheTtlSeconds());
  }

  private ChatActionRecommendationPayload cacheRecommendation(ChatActionRecommendationPayload payload, int ttlSeconds) {
    int effectiveTtlSeconds = Math.max(1, ttlSeconds);
    redisTemplate.opsForValue().set(
        buildCacheKey(payload.getChatRoomId(), payload.getTargetMemberId()),
        payload,
        effectiveTtlSeconds,
        TimeUnit.SECONDS
    );
    return payload;
  }

  // 같은 방/사용자에 대해 짧은 시간 안에 LLM을 다시 호출하지 않도록 막는다.
  private boolean acquireLlmCooldown(UUID chatRoomId, UUID viewerId) {
    Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
        buildLlmCooldownKey(chatRoomId, viewerId),
        "1",
        getLlmCooldownSeconds(),
        TimeUnit.SECONDS
    );
    return !Boolean.FALSE.equals(acquired);
  }

  // 동일한 액션을 짧은 시간 안에 반복 추천하지 않도록 막는다.
  private boolean isSameActionOnCooldown(UUID chatRoomId, UUID viewerId, ChatRecommendedAction action) {
    if (action == null || action.isNone()) {
      return false;
    }
    Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
        buildActionCooldownKey(chatRoomId, viewerId, action),
        "1",
        getSameActionCooldownSeconds(),
        TimeUnit.SECONDS
    );
    return Boolean.FALSE.equals(acquired);
  }

  // 최근에 취소/거절 시스템 메시지가 있었다면 교환 완료 재추천을 잠시 멈춘다.
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
    return createdDate.plusSeconds(getTradeCompletionRetryCooldownSeconds()).isAfter(LocalDateTime.now());
  }

  // 특정 추천은 마지막 메시지 이후 일정 시간 무응답일 때만 노출한다.
  private Integer resolveDeferredRecommendationSeconds(ChatRecommendedAction action, List<ChatMessage> recentMessages) {
    if (action != ChatRecommendedAction.REQUEST_TRADE_COMPLETION) {
      return null;
    }

    ChatMessage latestMessage = recentMessages.isEmpty() ? null : recentMessages.get(recentMessages.size() - 1);
    if (latestMessage == null || latestMessage.getCreatedDate() == null) {
      return null;
    }

    LocalDateTime availableAt = latestMessage.getCreatedDate().plusSeconds(getTradeCompletionInactivitySeconds());
    if (!availableAt.isAfter(LocalDateTime.now())) {
      return null;
    }

    // 아직 시점이 안 됐으면 남은 초만큼 NONE 을 캐시해서 그 전에는 재추천이 보이지 않게 한다.
    return (int) java.time.Duration.between(LocalDateTime.now(), availableAt).getSeconds();
  }

  // 교환 완료 요청이 떠 있는 동안에는 취소/확인/거절을 룰 우선으로 정리한다.
  private ChatRecommendationDecision resolveTradeCompletionRequestedDecision(List<ChatMessage> recentMessages,
                                                                            Set<ChatRecommendedAction> allowedActions) {
    if (allowedActions.contains(ChatRecommendedAction.CANCEL_TRADE_COMPLETION_REQUEST)) {
      return new ChatRecommendationDecision(
          ChatRecommendedAction.CANCEL_TRADE_COMPLETION_REQUEST,
          defaultReason(ChatRecommendedAction.CANCEL_TRADE_COMPLETION_REQUEST)
      );
    }

    String latestMeaningfulText = findLatestMeaningfulText(recentMessages);
    if (allowedActions.contains(ChatRecommendedAction.REJECT_TRADE_COMPLETION_REQUEST)
        && containsKeyword(latestMeaningfulText, ChatRecommendationKeywords.REJECT_KEYWORDS)) {
      return new ChatRecommendationDecision(
          ChatRecommendedAction.REJECT_TRADE_COMPLETION_REQUEST,
          defaultReason(ChatRecommendedAction.REJECT_TRADE_COMPLETION_REQUEST)
      );
    }

    if (allowedActions.contains(ChatRecommendedAction.CONFIRM_TRADE_COMPLETION)
        && containsKeyword(latestMeaningfulText, ChatRecommendationKeywords.TRADE_COMPLETION_KEYWORDS)) {
      return new ChatRecommendationDecision(
          ChatRecommendedAction.CONFIRM_TRADE_COMPLETION,
          defaultReason(ChatRecommendedAction.CONFIRM_TRADE_COMPLETION)
      );
    }

    return null;
  }

  // 룰로 확정은 어렵지만 추천 신호가 있을 때만 LLM을 호출한다.
  private boolean shouldAttemptLlm(UUID chatRoomId, UUID viewerId, List<ChatMessage> recentMessages) {
    ChatMessage latestMessage = recentMessages.isEmpty() ? null : recentMessages.get(recentMessages.size() - 1);
    if (latestMessage == null || latestMessage.getType() != MessageType.TEXT) {
      return false;
    }

    String latestText = normalizeKeywordSource(latestMessage.getContent());
    if (isTrivialText(latestText)) {
      return false;
    }

    if (!hasRecommendationSignal(recentMessages)) {
      return false;
    }

    // 최근에 이미 같은 사용자 기준 LLM 호출이 있었다면 룰 결과만 쓰고 모델 호출은 미룬다.
    return !Boolean.TRUE.equals(redisTemplate.hasKey(buildLlmCooldownKey(chatRoomId, viewerId)));
  }

  // 위치 공유를 바로 추천해도 되는 강한 신호를 최근 대화에서 찾는다.
  private boolean shouldRecommendLocation(UUID viewerId, List<ChatMessage> recentMessages) {
    ChatMessage latestTextMessage = findLatestTextMessage(recentMessages);
    if (latestTextMessage == null) {
      return false;
    }

    String latestText = normalizeKeywordSource(latestTextMessage.getContent());
    if (viewerId.equals(latestTextMessage.getSenderId())
        && containsKeyword(latestText, ChatRecommendationKeywords.LOCATION_DIRECT_COMMIT_KEYWORDS)) {
      return true;
    }

    if (!viewerId.equals(latestTextMessage.getSenderId())
        && containsKeyword(latestText, ChatRecommendationKeywords.LOCATION_DIRECT_REQUEST_KEYWORDS)) {
      return true;
    }

    return recentMessages.stream()
        .filter(message -> message.getType() == MessageType.TEXT)
        .filter(message -> !viewerId.equals(message.getSenderId()))
        .map(message -> normalizeKeywordSource(message.getContent()))
        .filter(text -> !isTrivialText(text))
        .anyMatch(text -> containsKeyword(text, ChatRecommendationKeywords.LOCATION_DIRECT_REQUEST_KEYWORDS));
  }

  // 거래가 마무리된 톤이 명확할 때는 교환 완료 요청을 룰로 바로 추천한다.
  private boolean shouldRecommendTradeCompletion(List<ChatMessage> recentMessages, String latestText) {
    if (containsKeyword(latestText, ChatRecommendationKeywords.TRADE_COMPLETION_KEYWORDS)) {
      return true;
    }

    List<String> meaningfulTexts = recentMessages.stream()
        .filter(message -> message.getType() == MessageType.TEXT)
        .map(ChatMessage::getContent)
        .map(this::normalizeKeywordSource)
        .filter(text -> !isTrivialText(text))
        .toList();
    int startIndex = Math.max(0, meaningfulTexts.size() - 2);
    return meaningfulTexts.subList(startIndex, meaningfulTexts.size()).stream()
        .anyMatch(text -> containsKeyword(text, ChatRecommendationKeywords.TRADE_COMPLETION_KEYWORDS));
  }

  // 룰로 확정 못 한 경우에도 호출 가치가 있는 메시지인지 키워드로 1차 필터링한다.
  private boolean hasRecommendationSignal(List<ChatMessage> recentMessages) {
    return recentMessages.stream()
        .filter(message -> message.getType() == MessageType.TEXT)
        .map(ChatMessage::getContent)
        .map(this::normalizeKeywordSource)
        .filter(text -> !isTrivialText(text))
        .anyMatch(text ->
            containsKeyword(text, ChatRecommendationKeywords.LOCATION_DIRECT_REQUEST_KEYWORDS)
                || containsKeyword(text, ChatRecommendationKeywords.LOCATION_DIRECT_COMMIT_KEYWORDS)
                || containsKeyword(text, ChatRecommendationKeywords.LOCATION_SIGNAL_KEYWORDS)
                || containsKeyword(text, ChatRecommendationKeywords.TRADE_COMPLETION_KEYWORDS)
        );
  }

  // 프롬프트에는 실제 판단에 도움이 되는 사용자 메시지만 남겨 토큰을 줄인다.
  private boolean isPromptRelevantMessage(ChatMessage message) {
    return message.getType() == MessageType.TEXT
        || message.getType() == MessageType.IMAGE
        || message.getType() == MessageType.LOCATION;
  }

  // 최근 텍스트 중 마지막 의미 있는 문장을 찾아 후속 상태 판단에 재사용한다.
  private String findLatestMeaningfulText(List<ChatMessage> recentMessages) {
    for (int index = recentMessages.size() - 1; index >= 0; index--) {
      ChatMessage recentMessage = recentMessages.get(index);
      if (recentMessage.getType() != MessageType.TEXT) {
        continue;
      }
      String normalized = normalizeKeywordSource(recentMessage.getContent());
      if (!isTrivialText(normalized)) {
        return normalized;
      }
    }
    return "";
  }

  // 최근 텍스트 메시지 자체가 필요할 때 가장 마지막 텍스트만 꺼낸다.
  private ChatMessage findLatestTextMessage(List<ChatMessage> recentMessages) {
    for (int index = recentMessages.size() - 1; index >= 0; index--) {
      ChatMessage recentMessage = recentMessages.get(index);
      if (recentMessage.getType() == MessageType.TEXT) {
        return recentMessage;
      }
    }
    return null;
  }

  // 키워드 비교 전 공백/대소문자 차이를 정리한다.
  private String normalizeKeywordSource(String content) {
    if (content == null) {
      return "";
    }
    return content.replace('\n', ' ')
        .replace('\r', ' ')
        .trim()
        .toLowerCase(Locale.ROOT);
  }

  // 짧은 추임새나 이모티콘 수준 메시지는 추천 판단에서 제외한다.
  private boolean isTrivialText(String text) {
    if (isBlank(text)) {
      return true;
    }

    String compact = text.replace(" ", "");
    if (ChatRecommendationKeywords.TRIVIAL_TEXTS.contains(compact)) {
      return true;
    }
    if (compact.length() <= getSafeTrivialMessageLength()
        && !containsKeyword(compact, ChatRecommendationKeywords.LOCATION_DIRECT_REQUEST_KEYWORDS)
        && !containsKeyword(compact, ChatRecommendationKeywords.TRADE_COMPLETION_KEYWORDS)) {
      return true;
    }
    return compact.matches("[ㅋㅎㅠㅜ!?.,~]+");
  }

  // 지정된 키워드 묶음 중 하나라도 포함되는지 확인한다.
  private boolean containsKeyword(String source, List<String> keywords) {
    if (isBlank(source)) {
      return false;
    }
    for (String keyword : keywords) {
      if (source.contains(keyword.toLowerCase(Locale.ROOT))) {
        return true;
      }
    }
    return false;
  }

  // 추천 payload 생성 시 공통 필드를 한곳에서 맞춘다.
  private ChatActionRecommendationPayload buildPayload(UUID chatRoomId, UUID viewerId, String basedOnMessageId,
                                                       ChatRecommendedAction action, String reason) {
    if (action == null || action.isNone()) {
      return ChatActionRecommendationPayload.none(chatRoomId, viewerId, basedOnMessageId);
    }

    return ChatActionRecommendationPayload.builder()
        .chatRoomId(chatRoomId)
        .targetMemberId(viewerId)
        .action(action)
        .reason(normalizeReason(action, reason))
        .basedOnMessageId(basedOnMessageId)
        .createdDate(LocalDateTime.now())
        .build();
  }

  // LLM reason 이 없더라도 프론트가 바로 쓸 수 있도록 액션별 기본 이유를 채운다.
  private String defaultReason(ChatRecommendedAction action) {
    if (action == null || action.isNone()) {
      return null;
    }
    return switch (action) {
      case SEND_LOCATION -> "만날 장소를 정하는 흐름이라 위치 공유를 추천해요.";
      case REQUEST_TRADE_COMPLETION -> "거래가 마무리된 분위기라 교환 완료 요청을 추천해요.";
      case CANCEL_TRADE_COMPLETION_REQUEST -> "이미 보낸 교환 완료 요청을 취소할 수 있어요.";
      case REJECT_TRADE_COMPLETION_REQUEST -> "상대방의 교환 완료 요청을 거절할 수 있어요.";
      case CONFIRM_TRADE_COMPLETION -> "상대방의 교환 완료 요청을 확인하고 완료 처리할 수 있어요.";
      case NONE -> null;
    };
  }

  // Redis key 를 한곳에서 관리해 추천 캐시와 쓰로틀 키 충돌을 막는다.
  private String buildCacheKey(UUID chatRoomId, UUID viewerId) {
    return ChatRecommendationConstants.CACHE_KEY_PREFIX + chatRoomId + ":" + viewerId;
  }

  private String buildLlmCooldownKey(UUID chatRoomId, UUID viewerId) {
    return ChatRecommendationConstants.LLM_COOLDOWN_KEY_PREFIX + chatRoomId + ":" + viewerId;
  }

  private String buildActionCooldownKey(UUID chatRoomId, UUID viewerId, ChatRecommendedAction action) {
    return ChatRecommendationConstants.ACTION_COOLDOWN_KEY_PREFIX + chatRoomId + ":" + viewerId + ":" + action.name();
  }

  // 운영 중 system_config 로도 조정할 수 있게 숫자 설정은 캐시된 시스템 설정값을 우선 본다.
  private int getLlmCooldownSeconds() {
    return getPositiveIntConfig(ChatRecommendationConstants.CONFIG_KEY_LLM_COOLDOWN_SECONDS, policyProperties.getLlmCooldownSeconds());
  }

  private int getSameActionCooldownSeconds() {
    return getPositiveIntConfig(
        ChatRecommendationConstants.CONFIG_KEY_SAME_ACTION_COOLDOWN_SECONDS,
        policyProperties.getSameActionCooldownSeconds()
    );
  }

  private int getCacheTtlSeconds() {
    return getPositiveIntConfig(ChatRecommendationConstants.CONFIG_KEY_CACHE_TTL_SECONDS, policyProperties.getCacheTtlSeconds());
  }

  private int getTradeCompletionRetryCooldownSeconds() {
    return getPositiveIntConfig(
        ChatRecommendationConstants.CONFIG_KEY_TRADE_COMPLETION_RETRY_COOLDOWN_SECONDS,
        policyProperties.getTradeCompletionRetryCooldownSeconds()
    );
  }

  private int getTradeCompletionInactivitySeconds() {
    return getPositiveIntConfig(
        ChatRecommendationConstants.CONFIG_KEY_TRADE_COMPLETION_INACTIVITY_SECONDS,
        policyProperties.getTradeCompletionInactivitySeconds()
    );
  }

  private int getSafeRecentMessageLimit() {
    return getPositiveProperty("ai.chat.recommendation.prompt.recent-message-limit", promptProperties.getRecentMessageLimit(), 6);
  }

  private int getSafeMaxPromptMessageLength() {
    return getPositiveProperty("ai.chat.recommendation.max-prompt-message-length", policyProperties.getMaxPromptMessageLength(), 120);
  }

  private int getSafeTrivialMessageLength() {
    return getPositiveProperty("ai.chat.recommendation.trivial-message-length", policyProperties.getTrivialMessageLength(), 2);
  }

  private int getSafeMaxOutputTokens() {
    return getPositiveProperty(
        "ai.chat.recommendation.prompt.generation-config.max-output-tokens",
        promptProperties.getGenerationConfig().getMaxOutputTokens(),
        24
    );
  }

  private double getSafeTemperature() {
    double temperature = promptProperties.getGenerationConfig().getTemperature();
    if (temperature < 0d) {
      log.warn("채팅 추천 temperature 설정이 0 미만이라 기본값을 사용합니다. value={}, default={}", temperature, 0.0d);
      return 0.0d;
    }
    return temperature;
  }

  private int getPositiveIntConfig(String key, int defaultValue) {
    String configuredValue = systemConfigCacheService.get(key);
    if (isBlank(configuredValue)) {
      return getPositiveDefault(key, defaultValue);
    }
    try {
      int parsedValue = Integer.parseInt(configuredValue);
      if (parsedValue < 1) {
        int safeDefaultValue = getPositiveDefault(key, defaultValue);
        log.warn("채팅 추천 숫자 설정이 1 미만이라 기본값을 사용합니다. key={}, value={}, default={}",
            key, parsedValue, safeDefaultValue);
        return safeDefaultValue;
      }
      return parsedValue;
    } catch (NumberFormatException numberFormatException) {
      int safeDefaultValue = getPositiveDefault(key, defaultValue);
      log.warn("채팅 추천 숫자 설정 파싱 실패. key={}, value={}, default={}", key, configuredValue, safeDefaultValue);
      return safeDefaultValue;
    }
  }

  private int getPositiveProperty(String propertyName, int configuredValue, int fallbackValue) {
    if (configuredValue < 1) {
      int safeFallbackValue = Math.max(1, fallbackValue);
      log.warn("채팅 추천 로컬 설정이 1 미만이라 기본값을 사용합니다. key={}, value={}, default={}",
          propertyName, configuredValue, safeFallbackValue);
      return safeFallbackValue;
    }
    return configuredValue;
  }

  private int getPositiveDefault(String key, int defaultValue) {
    int safeDefaultValue = Math.max(1, defaultValue);
    if (safeDefaultValue != defaultValue) {
      log.warn("채팅 추천 기본 설정도 1 미만이라 1로 보정합니다. key={}, default={}", key, defaultValue);
    }
    return safeDefaultValue;
  }

  // null/blank 체크를 공통 처리한다.
  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
