package com.romrom.chat.service;

import com.romrom.chat.dto.ChatRoomRequest;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.mongo.ChatUserState;
import com.romrom.chat.entity.mongo.MessageType;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.chat.repository.mongo.ChatMessageRepository;
import com.romrom.chat.repository.mongo.ChatUserStateRepository;
import com.romrom.chat.repository.postgres.ChatRoomRepository;
import com.romrom.common.constant.TradeStatus;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.item.repository.postgres.TradeRequestHistoryRepository;
import com.romrom.member.service.MemberBlockService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatTradeCompletionService {
  private final ChatRoomRepository chatRoomRepository;
  private final ChatUserStateRepository chatUserStateRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final TradeRequestHistoryRepository tradeRequestHistoryRepository;
  private final MemberBlockService memberBlockService;
  private final ChatMessageService chatMessageService;

  @Transactional
  public void requestTradeCompletion(ChatRoomRequest request) {
    ChatRoom room = validateChatRoomMember(request.getMember().getMemberId(), request.getChatRoomId());
    UUID actorId = request.getMember().getMemberId();
    UUID opponentId = getOpponentId(room, actorId);
    validateOpponentState(room.getChatRoomId(), opponentId);
    memberBlockService.verifyNotBlocked(actorId, opponentId);

    TradeRequestHistory tradeRequestHistory = getTradeRequestHistory(room);
    if (tradeRequestHistory.getTradeStatus() != TradeStatus.CHATTING) {
      log.error("교환 완료 요청 불가 상태. tradeRequestHistoryId={}, tradeStatus={}",
          tradeRequestHistory.getTradeRequestHistoryId(), tradeRequestHistory.getTradeStatus());
      throw new CustomException(ErrorCode.TRADE_COMPLETION_REQUEST_NOT_ALLOWED);
    }

    tradeRequestHistory.requestTradeCompletion();
    chatMessageService.sendTradeSystemMessage(
        room,
        actorId,
        opponentId,
        MessageType.TRADE_COMPLETE_REQUEST,
        "교환 완료 요청이 전송되었습니다."
    );
  }

  @Transactional
  public void cancelTradeCompletionRequest(ChatRoomRequest request) {
    ChatRoom room = validateChatRoomMember(request.getMember().getMemberId(), request.getChatRoomId());
    UUID actorId = request.getMember().getMemberId();
    UUID opponentId = getOpponentId(room, actorId);
    validateOpponentState(room.getChatRoomId(), opponentId);
    memberBlockService.verifyNotBlocked(actorId, opponentId);

    TradeRequestHistory tradeRequestHistory = getTradeRequestHistory(room);
    ChatMessage pendingRequestMessage = getPendingTradeCompletionRequest(room.getChatRoomId(), tradeRequestHistory);

    if (!pendingRequestMessage.getSenderId().equals(actorId)) {
      log.error("교환 완료 요청 취소 권한 없음. chatRoomId={}, actorId={}, requestSenderId={}",
          room.getChatRoomId(), actorId, pendingRequestMessage.getSenderId());
      throw new CustomException(ErrorCode.TRADE_COMPLETION_REQUEST_FORBIDDEN);
    }

    tradeRequestHistory.cancelTradeCompletionRequest();
    chatMessageService.sendTradeSystemMessage(
        room,
        actorId,
        opponentId,
        MessageType.TRADE_COMPLETE_REQUEST_CANCELED,
        "교환 완료 요청이 취소되었습니다."
    );
  }

  @Transactional
  public void rejectTradeCompletionRequest(ChatRoomRequest request) {
    ChatRoom room = validateChatRoomMember(request.getMember().getMemberId(), request.getChatRoomId());
    UUID actorId = request.getMember().getMemberId();
    UUID opponentId = getOpponentId(room, actorId);
    validateOpponentState(room.getChatRoomId(), opponentId);
    memberBlockService.verifyNotBlocked(actorId, opponentId);

    TradeRequestHistory tradeRequestHistory = getTradeRequestHistory(room);
    ChatMessage pendingRequestMessage = getPendingTradeCompletionRequest(room.getChatRoomId(), tradeRequestHistory);

    if (pendingRequestMessage.getSenderId().equals(actorId)) {
      log.error("교환 완료 요청 발신자는 본인 요청을 거절할 수 없습니다. chatRoomId={}, actorId={}",
          room.getChatRoomId(), actorId);
      throw new CustomException(ErrorCode.TRADE_COMPLETION_REQUEST_FORBIDDEN);
    }

    tradeRequestHistory.rejectTradeCompletionRequest();
    chatMessageService.sendTradeSystemMessage(
        room,
        actorId,
        opponentId,
        MessageType.TRADE_COMPLETE_REQUEST_REJECTED,
        "교환 완료 요청이 거절되었습니다."
    );
  }

  @Transactional
  public void confirmTradeCompletion(ChatRoomRequest request) {
    ChatRoom room = validateChatRoomMember(request.getMember().getMemberId(), request.getChatRoomId());
    UUID actorId = request.getMember().getMemberId();
    UUID opponentId = getOpponentId(room, actorId);
    validateOpponentState(room.getChatRoomId(), opponentId);
    memberBlockService.verifyNotBlocked(actorId, opponentId);

    TradeRequestHistory tradeRequestHistory = getTradeRequestHistory(room);
    ChatMessage pendingRequestMessage = getPendingTradeCompletionRequest(room.getChatRoomId(), tradeRequestHistory);

    if (pendingRequestMessage.getSenderId().equals(actorId)) {
      log.error("교환 완료 요청 발신자는 본인 요청을 확인할 수 없습니다. chatRoomId={}, actorId={}",
          room.getChatRoomId(), actorId);
      throw new CustomException(ErrorCode.TRADE_COMPLETION_REQUEST_FORBIDDEN);
    }

    tradeRequestHistory.completeTrade();
    chatMessageService.sendTradeSystemMessage(
        room,
        actorId,
        opponentId,
        MessageType.TRADE_COMPLETED,
        "교환이 완료되었습니다."
    );
  }

  private ChatMessage getPendingTradeCompletionRequest(UUID chatRoomId, TradeRequestHistory tradeRequestHistory) {
    TradeStatus currentTradeStatus = tradeRequestHistory.getTradeStatus();

    if (currentTradeStatus == TradeStatus.TRADED) {
      log.error("이미 완료된 거래에 대한 교환 완료 요청 처리. tradeRequestHistoryId={}, tradeStatus={}",
          tradeRequestHistory.getTradeRequestHistoryId(), currentTradeStatus);
      throw new CustomException(ErrorCode.TRADE_ALREADY_COMPLETED);
    }

    if (currentTradeStatus != TradeStatus.TRADE_COMPLETE_REQUESTED) {
      log.error("진행 중인 교환 완료 요청이 없는 상태입니다. tradeRequestHistoryId={}, tradeStatus={}",
          tradeRequestHistory.getTradeRequestHistoryId(), currentTradeStatus);
      throw new CustomException(ErrorCode.TRADE_COMPLETION_REQUEST_NOT_FOUND);
    }

    ChatMessage latestTradeCompletionMessage = chatMessageRepository
        .findFirstByChatRoomIdAndTypeInOrderByCreatedDateDesc(chatRoomId, MessageType.tradeCompletionTypes())
        .orElseThrow(() -> {
          log.error("MongoDB에 교환 완료 요청 메시지가 없습니다. chatRoomId={}", chatRoomId);
          return new CustomException(ErrorCode.TRADE_COMPLETION_MESSAGE_NOT_FOUND);
        });

    if (latestTradeCompletionMessage.getType() != MessageType.TRADE_COMPLETE_REQUEST) {
      log.error("교환 완료 요청 상태와 마지막 시스템 메시지가 일치하지 않습니다. chatRoomId={}, latestType={}",
          chatRoomId, latestTradeCompletionMessage.getType());
      throw new CustomException(ErrorCode.TRADE_COMPLETION_STATE_MISMATCH);
    }

    return latestTradeCompletionMessage;
  }

  private TradeRequestHistory getTradeRequestHistory(ChatRoom room) {
    return tradeRequestHistoryRepository.findByTradeRequestHistoryIdWithItems(room.getTradeRequestHistory().getTradeRequestHistoryId())
        .orElseThrow(() -> new CustomException(ErrorCode.TRADE_REQUEST_NOT_FOUND));
  }

  private void validateOpponentState(UUID chatRoomId, UUID opponentId) {
    ChatUserState opponentState = chatUserStateRepository.findByChatRoomIdAndMemberId(chatRoomId, opponentId)
        .orElseThrow(() -> new CustomException(ErrorCode.CHAT_USER_STATE_NOT_FOUND));

    if (opponentState.isDeleted()) {
      log.error("상대방이 삭제한 채팅방에서는 교환 완료 요청을 처리할 수 없습니다. chatRoomId={}, opponentId={}", chatRoomId, opponentId);
      throw new CustomException(ErrorCode.CANNOT_SEND_MESSAGE_TO_DELETED_CHATROOM);
    }
  }

  private UUID getOpponentId(ChatRoom room, UUID actorId) {
    return room.getTradeReceiver().getMemberId().equals(actorId)
        ? room.getTradeSender().getMemberId()
        : room.getTradeReceiver().getMemberId();
  }

  private ChatRoom validateChatRoomMember(UUID memberId, UUID chatRoomId) {
    ChatRoom chatRoom = chatRoomRepository.findByChatRoomIdWithSenderAndReceiver(chatRoomId)
        .orElseThrow(() -> new CustomException(ErrorCode.CHATROOM_NOT_FOUND));

    if (!chatRoom.isMember(memberId)) {
      log.error("채팅방 회원 검증 오류 : 요청자는 채팅방 멤버가 아닙니다.");
      throw new CustomException(ErrorCode.NOT_CHATROOM_MEMBER);
    }
    return chatRoom;
  }
}
