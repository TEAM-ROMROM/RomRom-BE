package com.romrom.chat.service.recommendation;

import com.romrom.chat.constant.ChatRecommendationConstants;
import com.romrom.chat.dto.ChatRecommendedAction;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.mongo.MessageType;
import com.romrom.chat.entity.postgres.ChatRoom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.suhsaechan.ai.model.ChatRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * LLM 추천 요청 조립: 모델·생성 옵션, 시스템 instruction, 사용자 관점 대화 요약 프롬프트
 * 프롬프트 형식: trade_status > allowed_actions > 액션 설명 > recent_messages > return_json_only
 */
@Component
@RequiredArgsConstructor
public class ChatRecommendationPromptBuilder {

  // 생성 옵션 키 (Ollama 호환)
  private static final String OPTION_KEY_TEMPERATURE = "temperature";
  private static final String OPTION_KEY_MAX_OUTPUT_TOKENS = "num_predict";

  // 프롬프트 내 발신자 표기
  private static final String SENDER_ROLE_SELF = "SELF";
  private static final String SENDER_ROLE_OPPONENT = "OPPONENT";

  private final ChatRecommendationSettings settings;

  /**
   * 허용 액션 안에서만 JSON 응답하도록 하는 LLM 요청 생성
   */
  public ChatRequest buildChatRequest(ChatRoom room, UUID viewerId, List<ChatMessage> recentMessages,
                                      Set<ChatRecommendedAction> allowedActions) {
    String model = settings.resolveRecommendationModel();
    String userPrompt = buildUserPrompt(room, viewerId, recentMessages, allowedActions);

    Map<String, Object> generationOptions = new HashMap<>();
    generationOptions.put(OPTION_KEY_TEMPERATURE, settings.getTemperature());
    generationOptions.put(OPTION_KEY_MAX_OUTPUT_TOKENS, settings.getMaxOutputTokens());

    // instruction: 관리자 런타임 값 > yml 기본값
    String resolvedInstruction = settings.resolveRecommendationInstruction();

    return ChatRequest.builder()
        .model(model)
        .messages(List.of(
            kr.suhsaechan.ai.model.ChatMessage.system(resolvedInstruction),
            kr.suhsaechan.ai.model.ChatMessage.user(userPrompt)
        ))
        .stream(false)
        .options(generationOptions)
        .responseSchema(ChatRecommendationConstants.RESPONSE_SCHEMA)
        .build();
  }

  // 현재 사용자 관점 대화 요약 + 허용 액션 목록 > 사용자 프롬프트
  private String buildUserPrompt(ChatRoom room, UUID viewerId, List<ChatMessage> recentMessages,
                                 Set<ChatRecommendedAction> allowedActions) {
    StringBuilder prompt = new StringBuilder();
    prompt.append("trade_status=").append(room.getTradeRequestHistory().getTradeStatus()).append('\n');
    prompt.append("allowed_actions=").append(String.join(", ", allowedActions.stream().map(Enum::name).toList())).append('\n');
    prompt.append("allowed_action_descriptions=").append(buildAllowedActionDescriptions(allowedActions)).append('\n');
    prompt.append("recent_messages:\n");

    // 메시지 1건 > "- {SELF|OPPONENT} [{type}] {내용}"
    for (ChatMessage recentMessage : recentMessages.stream().filter(this::isPromptRelevantMessage).toList()) {
      String senderRole = viewerId.equals(recentMessage.getSenderId()) ? SENDER_ROLE_SELF : SENDER_ROLE_OPPONENT;
      prompt.append("- ")
          .append(senderRole)
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

  // 허용 액션 > enum 의미 설명 (" | " 구분, NONE 선택 기준 항상 포함)
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

  // 메시지 내용 정리 (빈 값 > 대체 문구, 개행 제거, 최대 길이 제한)
  private String normalizeMessageContent(String content, String fallbackContent) {
    String normalizedContent = !StringUtils.hasText(content)
        ? fallbackContent
        : content.replace('\n', ' ').replace('\r', ' ').trim();
    if (normalizedContent == null) {
      return "";
    }
    int maxPromptMessageLength = settings.getMaxPromptMessageLength();
    if (normalizedContent.length() > maxPromptMessageLength) {
      return normalizedContent.substring(0, maxPromptMessageLength);
    }
    return normalizedContent;
  }

  // 프롬프트 포함 대상 3종: TEXT, IMAGE, LOCATION (토큰 절약)
  private boolean isPromptRelevantMessage(ChatMessage message) {
    return message.getType() == MessageType.TEXT
        || message.getType() == MessageType.IMAGE
        || message.getType() == MessageType.LOCATION;
  }
}
