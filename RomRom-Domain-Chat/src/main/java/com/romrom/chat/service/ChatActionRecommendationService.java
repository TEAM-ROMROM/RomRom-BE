package com.romrom.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.romrom.chat.dto.ChatActionRecommendationPayload;
import com.romrom.chat.dto.ChatRecommendedAction;
import com.romrom.chat.dto.ChatRecommendationDecision;
import com.romrom.chat.dto.ChatRecommendationRawResponse;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.mongo.MessageType;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.chat.event.ChatRecommendationRequestedEvent;
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
import kr.suhsaechan.ai.model.ChatRequest;
import kr.suhsaechan.ai.model.ChatResponse;
import kr.suhsaechan.ai.model.JsonSchema;
import kr.suhsaechan.ai.service.SuhAiderEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
// 최근 대화와 거래 상태를 보고 현재 사용자에게 보여줄 다음 행동 추천을 계산한다.
public class ChatActionRecommendationService {

  private static final String DEFAULT_CHAT_MODEL = "granite4:micro-h";
  private static final JsonSchema RESPONSE_SCHEMA = JsonSchema.object("action", "reason")
      .property("action", "string")
      .property("reason", "string")
      .required("action");

  private final ChatRoomRepository chatRoomRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final ChatWebSocketService chatWebSocketService;
  private final SuhAiderEngine suhAiderEngine;
  private final SystemConfigCacheService systemConfigCacheService;
  private final ChatRecommendationPromptProperties promptProperties;
  private final ObjectMapper objectMapper;

  // messages/get 응답에서 현재 사용자가 볼 추천 액션을 계산한다.
  @Transactional(readOnly = true)
  public ChatActionRecommendationPayload recommendForViewer(ChatRoom room, UUID viewerId, List<ChatMessage> recentMessages) {
    if (!isRecommendationEnabled()) {
      return null;
    }

    List<ChatMessage> normalizedMessages = normalizeRecentMessages(room.getChatRoomId(), recentMessages);
    String basedOnMessageId = normalizedMessages.isEmpty()
        ? null
        : normalizedMessages.get(normalizedMessages.size() - 1).getChatMessageId();

    EnumSet<ChatRecommendedAction> allowedActions = resolveAllowedActions(room, viewerId, normalizedMessages);
    if (allowedActions.isEmpty()) {
      return ChatActionRecommendationPayload.none(room.getChatRoomId(), viewerId, basedOnMessageId);
    }

    ChatRecommendationDecision decision = requestRecommendation(room, viewerId, normalizedMessages, allowedActions);
    if (decision == null || decision.getAction() == null || !isAllowed(decision.getAction(), allowedActions)) {
      return ChatActionRecommendationPayload.none(room.getChatRoomId(), viewerId, basedOnMessageId);
    }

    return ChatActionRecommendationPayload.builder()
        .chatRoomId(room.getChatRoomId())
        .targetMemberId(viewerId)
        .action(decision.getAction())
        .reason(decision.getReason())
        .basedOnMessageId(basedOnMessageId)
        .createdDate(LocalDateTime.now())
        .build();
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
      String model = systemConfigCacheService.getOrDefault("ai.ollama.chat-model", DEFAULT_CHAT_MODEL);
      String userPrompt = buildUserPrompt(room, viewerId, recentMessages, allowedActions);

      Map<String, Object> options = new HashMap<>();
      options.put("temperature", promptProperties.getGenerationConfig().getTemperature());
      options.put("num_predict", promptProperties.getGenerationConfig().getMaxOutputTokens());

      ChatRequest request = ChatRequest.builder()
          .model(model)
          .messages(List.of(
              kr.suhsaechan.ai.model.ChatMessage.system(promptProperties.getInstruction()),
              kr.suhsaechan.ai.model.ChatMessage.user(userPrompt)
          ))
          .stream(false)
          .options(options)
          .responseSchema(RESPONSE_SCHEMA)
          .build();

      ChatResponse response = suhAiderEngine.chat(request);
      String rawContent = extractContent(response);
      if (isBlank(rawContent)) {
        log.warn("채팅 추천 LLM 응답이 비어있습니다. roomId={}, viewerId={}", room.getChatRoomId(), viewerId);
        return null;
      }

      ChatRecommendationRawResponse rawResponse = objectMapper.readValue(stripCodeFence(rawContent), ChatRecommendationRawResponse.class);
      ChatRecommendedAction action = toAction(rawResponse.getAction());
      return new ChatRecommendationDecision(action, normalizeReason(rawResponse.getReason()));
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
      return actions;
    }

    if (tradeStatus == TradeStatus.TRADE_COMPLETE_REQUESTED) {
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

  // 추천 판단에 쓸 최근 메시지를 조회하고 오래된 순으로 정렬해 정규화한다.
  private List<ChatMessage> normalizeRecentMessages(UUID chatRoomId, List<ChatMessage> recentMessages) {
    List<ChatMessage> messages = recentMessages;
    if (messages == null || messages.isEmpty()) {
      messages = chatMessageRepository.findByChatRoomIdOrderByCreatedDateDesc(
          chatRoomId,
          PageRequest.of(0, promptProperties.getRecentMessageLimit(), Sort.by(Sort.Direction.DESC, "createdDate"))
      ).getContent();
    }

    List<ChatMessage> sorted = new ArrayList<>(messages);
    sorted.sort((left, right) -> left.getCreatedDate().compareTo(right.getCreatedDate()));
    if (sorted.size() > promptProperties.getRecentMessageLimit()) {
      return new ArrayList<>(sorted.subList(sorted.size() - promptProperties.getRecentMessageLimit(), sorted.size()));
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
    prompt.append("viewer_id=").append(viewerId).append('\n');
    prompt.append("chat_room_id=").append(room.getChatRoomId()).append('\n');
    prompt.append("trade_status=").append(room.getTradeRequestHistory().getTradeStatus()).append('\n');
    prompt.append("allowed_actions=").append(String.join(", ", allowedActions.stream().map(Enum::name).toList())).append('\n');
    prompt.append("allowed_action_descriptions=").append(buildAllowedActionDescriptions(allowedActions)).append('\n');
    prompt.append("recent_messages:\n");

    for (ChatMessage recentMessage : recentMessages) {
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
        case SEND_LOCATION -> "SEND_LOCATION: 상대방에게 거래 위치 공유를 추천";
        case REQUEST_TRADE_COMPLETION -> "REQUEST_TRADE_COMPLETION: 교환이 끝난 분위기일 때 교환 완료 요청을 추천";
        case CANCEL_TRADE_COMPLETION_REQUEST -> "CANCEL_TRADE_COMPLETION_REQUEST: 내가 보낸 교환 완료 요청을 취소하는 행동 추천";
        case REJECT_TRADE_COMPLETION_REQUEST -> "REJECT_TRADE_COMPLETION_REQUEST: 상대방의 교환 완료 요청을 거절하는 행동 추천";
        case CONFIRM_TRADE_COMPLETION -> "CONFIRM_TRADE_COMPLETION: 상대방의 교환 완료 요청을 확인하고 완료 처리하는 행동 추천";
        case NONE -> "NONE: 추천 없음";
      });
    }
    descriptions.add("NONE: 추천할 행동이 없으면 선택");
    return String.join(" | ", descriptions);
  }

  // 너무 긴 메시지나 개행을 잘라 프롬프트 길이를 일정하게 유지한다.
  private String normalizeMessageContent(String content, String fallback) {
    String normalized = isBlank(content) ? fallback : content.replace('\n', ' ').replace('\r', ' ').trim();
    if (normalized == null) {
      return "";
    }
    if (normalized.length() > 220) {
      return normalized.substring(0, 220);
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
  private String normalizeReason(String reason) {
    if (isBlank(reason)) {
      return null;
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
  private boolean isRecommendationEnabled() {
    return promptProperties.isEnabled() && !isBlank(promptProperties.getInstruction());
  }

  // null/blank 체크를 공통 처리한다.
  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
