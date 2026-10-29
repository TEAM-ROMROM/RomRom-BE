package com.romrom.chat.stomp.presence;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * WebSocket 세션별 채팅방 열람 추적: 세션 등록, 읽음 채널 구독·해제, 연결 종료 시 퇴장 대상 계산 (#755)
 * 열람 기준은 읽음 채널(chat.read.{roomId}) 구독 > 앱이 방 화면에서만 구독·해제 (목록 화면은 chat.room만 구독)
 */
@Slf4j
@Component
public class ChatSessionPresenceRegistry {

  // 읽음 채널 구독 주소: /sub/chat.read.{roomId} 또는 변환 후 /exchange/chat.exchange/chat.read.{roomId}
  private static final Pattern READ_CHANNEL_DESTINATION_PATTERN =
      Pattern.compile("chat\\.read\\.([0-9a-fA-F-]{36})$");

  // 서버 인스턴스 로컬 기록 (블루그린 전환 중 다른 인스턴스 세션은 보이지 않음)
  private final Map<String, LiveChatSession> liveSessionBySessionId = new ConcurrentHashMap<>();

  /**
   * CONNECT 인증 성공 세션 등록
   */
  public void registerSession(String sessionId, UUID memberId) {
    if (sessionId == null || memberId == null) {
      return;
    }
    liveSessionBySessionId.put(sessionId, new LiveChatSession(memberId));
  }

  /**
   * SUBSCRIBE 기록 (읽음 채널만, 그 외 주소 무시)
   */
  public void registerSubscription(String sessionId, String subscriptionId, String destination) {
    UUID chatRoomId = extractReadChannelChatRoomId(destination);
    LiveChatSession liveSession = sessionId == null ? null : liveSessionBySessionId.get(sessionId);
    if (chatRoomId == null || liveSession == null || subscriptionId == null) {
      return;
    }
    liveSession.viewedChatRoomIdBySubscriptionId().put(subscriptionId, chatRoomId);
  }

  /**
   * UNSUBSCRIBE 기록 (구독 ID 기준)
   */
  public void removeSubscription(String sessionId, String subscriptionId) {
    LiveChatSession liveSession = sessionId == null ? null : liveSessionBySessionId.get(sessionId);
    if (liveSession == null || subscriptionId == null) {
      return;
    }
    liveSession.viewedChatRoomIdBySubscriptionId().remove(subscriptionId);
  }

  /**
   * 연결 종료 세션 제거 > 퇴장 대상 계산
   * 퇴장 대상: 이 세션이 보던 방 - 같은 회원의 다른 세션이 보는 방
   *
   * @return 등록되지 않은 세션이면 null (인증 전 종료, 중복 종료 이벤트)
   */
  public ChatSessionDisconnectResult removeSession(String sessionId) {
    LiveChatSession disconnectedSession = sessionId == null ? null : liveSessionBySessionId.remove(sessionId);
    if (disconnectedSession == null) {
      return null;
    }

    UUID memberId = disconnectedSession.memberId();
    boolean hasOtherLiveSession = false;
    Set<UUID> chatRoomIdsViewedByOtherSessions = new HashSet<>();
    for (LiveChatSession otherSession : liveSessionBySessionId.values()) {
      if (!memberId.equals(otherSession.memberId())) {
        continue;
      }
      hasOtherLiveSession = true;
      chatRoomIdsViewedByOtherSessions.addAll(otherSession.viewedChatRoomIdBySubscriptionId().values());
    }

    Set<UUID> chatRoomIdsToLeave = new HashSet<>(disconnectedSession.viewedChatRoomIdBySubscriptionId().values());
    chatRoomIdsToLeave.removeAll(chatRoomIdsViewedByOtherSessions);
    return new ChatSessionDisconnectResult(memberId, hasOtherLiveSession, chatRoomIdsToLeave);
  }

  /**
   * 읽음 채널 주소 > 채팅방 ID (형식 불일치 null)
   */
  static UUID extractReadChannelChatRoomId(String destination) {
    if (destination == null) {
      return null;
    }
    Matcher destinationMatcher = READ_CHANNEL_DESTINATION_PATTERN.matcher(destination);
    if (!destinationMatcher.find()) {
      return null;
    }
    try {
      return UUID.fromString(destinationMatcher.group(1));
    } catch (IllegalArgumentException e) {
      log.debug("읽음 채널 주소의 채팅방 ID 형식이 올바르지 않습니다. destination={}", destination);
      return null;
    }
  }

  // 구독 ID > 열람 중 채팅방 ID (같은 방 중복 구독도 구독 ID별로 기록)
  private record LiveChatSession(UUID memberId, Map<String, UUID> viewedChatRoomIdBySubscriptionId) {

    private LiveChatSession(UUID memberId) {
      this(memberId, new ConcurrentHashMap<>());
    }
  }

  /**
   * 연결 종료 결과: 같은 회원의 다른 세션 존재 여부, 퇴장 대상 채팅방
   */
  public record ChatSessionDisconnectResult(UUID memberId, boolean hasOtherLiveSession, Set<UUID> chatRoomIdsToLeave) {
  }
}
