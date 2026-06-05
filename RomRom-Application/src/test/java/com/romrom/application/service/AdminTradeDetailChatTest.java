package com.romrom.application.service;

import static me.suhsaechan.suhlogger.util.SuhLogger.lineLog;
import static me.suhsaechan.suhlogger.util.SuhLogger.timeLog;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.common.constant.TradeStatus;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.item.repository.postgres.TradeRequestHistoryRepository;
import com.romrom.web.RomBackApplication;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 관리자 거래 상세 - 채팅 전체 내역(chatMessages) 포함 검증.
 *
 * getTradeDetail 응답에 chatMessages 필드가 채워지는지 확인한다.
 * - 채팅방 있는 거래: 메시지가 시간순으로 채워짐 (또는 빈 채팅이면 빈 리스트)
 * - 채팅방 없는 거래(PENDING): chatMessages가 빈 리스트
 *
 * dev 프로파일(실 PostgreSQL + Mongo)로 실행해야 의미가 있다.
 */
@SpringBootTest(classes = RomBackApplication.class)
@ActiveProfiles("dev")
@Slf4j
class AdminTradeDetailChatTest {

  @Autowired
  AdminTradeService adminTradeService;

  @Autowired
  TradeRequestHistoryRepository tradeRequestHistoryRepository;

  @Test
  public void mainTest() {
    lineLog("테스트시작");

    lineLog(null);
    timeLog(this::getTradeDetail_임의거래1건_chatMessages_null_아님_테스트);
    lineLog(null);
    timeLog(this::getTradeDetail_채팅방있는거래_chatMessages_null_아님_테스트);
    lineLog(null);
    timeLog(this::getTradeDetail_PENDING거래_chatMessages_빈리스트_테스트);
    lineLog(null);

    lineLog("테스트종료");
  }

  // 데이터 상태와 무관하게: 거래가 1건이라도 있으면 getTradeDetail이 예외 없이 동작하고
  // chatMessages 필드가 null이 아닌 리스트로 채워지는지 검증 (와이어링 + 응답 구조 회귀 방지)
  public void getTradeDetail_임의거래1건_chatMessages_null_아님_테스트() {
    TradeRequestHistory anyTrade = tradeRequestHistoryRepository.findAll().stream()
        .findFirst()
        .orElse(null);

    if (anyTrade == null) {
      lineLog("거래 데이터 없어 테스트 스킵");
      return;
    }

    AdminRequest request = AdminRequest.builder()
        .tradeRequestHistoryId(anyTrade.getTradeRequestHistoryId())
        .build();
    AdminResponse response = adminTradeService.getTradeDetail(request);

    Assertions.assertNotNull(response.getTradeDetail(), "tradeDetail이 반환되어야 한다");
    Assertions.assertNotNull(response.getTradeDetail().getTradeRequestHistory(), "거래 이력이 포함되어야 한다");
    Assertions.assertNotNull(response.getTradeDetail().getChatMessages(),
        "chatMessages는 null이 아닌 리스트여야 한다 (채팅방 없으면 빈 리스트)");
    lineLog("임의 거래 상세 조회 성공: 상태=" + anyTrade.getTradeStatus()
        + ", chatMessages 수=" + response.getTradeDetail().getChatMessages().size());
  }

  // 채팅방이 있는 거래(CHATTING 이상): chatMessages가 null이 아니어야 한다 (빈 채팅이면 빈 리스트 허용)
  public void getTradeDetail_채팅방있는거래_chatMessages_null_아님_테스트() {
    TradeRequestHistory chattingTrade = tradeRequestHistoryRepository.findAll().stream()
        .filter(trade -> trade.getTradeStatus() == TradeStatus.CHATTING
            || trade.getTradeStatus() == TradeStatus.TRADE_COMPLETE_REQUESTED
            || trade.getTradeStatus() == TradeStatus.TRADED)
        .findFirst()
        .orElse(null);

    if (chattingTrade == null) {
      lineLog("채팅 가능 상태 거래 없어 테스트 스킵");
      return;
    }

    AdminRequest request = AdminRequest.builder()
        .tradeRequestHistoryId(chattingTrade.getTradeRequestHistoryId())
        .build();
    AdminResponse response = adminTradeService.getTradeDetail(request);
    List<?> chatMessages = response.getTradeDetail().getChatMessages();

    // 핵심: chatMessages 필드가 null이 아니고 (빈 리스트라도) 응답에 포함되어야 한다
    Assertions.assertNotNull(chatMessages, "채팅방 있는 거래의 chatMessages는 null이 아니어야 한다");
    lineLog("거래상태=" + chattingTrade.getTradeStatus() + ", chatMessages 수=" + chatMessages.size());
  }

  // PENDING 거래(채팅방 없음): chatMessages가 빈 리스트여야 한다
  public void getTradeDetail_PENDING거래_chatMessages_빈리스트_테스트() {
    TradeRequestHistory pendingTrade = tradeRequestHistoryRepository.findAll().stream()
        .filter(trade -> trade.getTradeStatus() == TradeStatus.PENDING)
        .findFirst()
        .orElse(null);

    if (pendingTrade == null) {
      lineLog("PENDING 거래 없어 테스트 스킵");
      return;
    }

    AdminRequest request = AdminRequest.builder()
        .tradeRequestHistoryId(pendingTrade.getTradeRequestHistoryId())
        .build();
    AdminResponse response = adminTradeService.getTradeDetail(request);
    List<?> chatMessages = response.getTradeDetail().getChatMessages();

    Assertions.assertNotNull(chatMessages, "PENDING 거래의 chatMessages는 null이 아닌 빈 리스트여야 한다");
    Assertions.assertTrue(chatMessages.isEmpty(), "PENDING 거래는 채팅방이 없어 chatMessages가 비어야 한다");
    lineLog("PENDING 거래 chatMessages 비어있음 확인");
  }
}
