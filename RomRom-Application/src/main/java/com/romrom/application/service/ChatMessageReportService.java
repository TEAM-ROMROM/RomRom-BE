package com.romrom.application.service;

import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.service.ChatMessageService;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.report.dto.ReportRequest;
import com.romrom.report.service.ReportService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채팅 메시지 신고 (#860): 채팅 도메인 확인(방 멤버·메시지 소속) > 신고 도메인 저장
 * Report 모듈이 Chat 모듈을 모르므로 두 도메인을 응용 계층에서 연결
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatMessageReportService {

  private final ChatMessageService chatMessageService;
  private final ReportService reportService;

  /**
   * 신고 접수: 필수값 > 방 멤버 확인 > 방 안 회원 메시지 조회 > 신고 저장 (대상 = 메시지 보낸 사람, 원본 본문 사본)
   */
  @Transactional
  public void reportChatMessage(ReportRequest request) {
    if (request.getChatRoomId() == null || request.getChatMessageId() == null) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    UUID reporterMemberId = request.getMember().getMemberId();
    chatMessageService.validateChatRoomMember(reporterMemberId, request.getChatRoomId());
    ChatMessage reportedMessage = chatMessageService.findMessageInChatRoom(request.getChatRoomId(),
        request.getChatMessageId());
    // 회원이 보낸 메시지(TEXT·IMAGE·LOCATION)만 신고 대상 (시스템·거래 안내 제외)
    if (reportedMessage.getType() == null || !reportedMessage.getType().isClientSendable()) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }

    // 원본 본문 사본 (삭제·숨김 메시지도 관리자 확인용으로 원문 보관)
    reportService.createChatMessageReport(request, reportedMessage.getSenderId(), reportedMessage.getContent());
    log.debug("[chat report] created : chatRoomId={}, chatMessageId={}", request.getChatRoomId(),
        request.getChatMessageId());
  }
}
