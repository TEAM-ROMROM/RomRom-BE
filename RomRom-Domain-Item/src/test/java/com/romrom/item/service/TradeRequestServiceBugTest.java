package com.romrom.item.service;

import static me.suhsaechan.suhlogger.util.SuhLogger.lineLog;
import static me.suhsaechan.suhlogger.util.SuhLogger.superLog;
import static me.suhsaechan.suhlogger.util.SuhLogger.timeLog;

import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.dto.TradeRequest;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.member.entity.Member;
import com.romrom.member.repository.MemberRepository;
import com.romrom.web.RomBackApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = RomBackApplication.class)
@ActiveProfiles("dev")
@Slf4j
class TradeRequestServiceBugTest {

  @Autowired
  TradeRequestService tradeRequestService;

  @Autowired
  ItemRepository itemRepository;

  @Autowired
  MemberRepository memberRepository;

  @Test
  public void mainTest() {
    lineLog("테스트시작");

    lineLog(null);
    timeLog(this::sendTradeRequest_삭제된giveItem으로거래요청시_ITEM_NOT_FOUND반환_테스트);
    lineLog(null);
    timeLog(this::sendTradeRequest_삭제된takeItem으로거래요청시_ITEM_NOT_FOUND반환_테스트);
    lineLog(null);

    lineLog("테스트종료");
  }

  // Bug 12: giveItem이 softDelete된 경우 → ITEM_NOT_FOUND
  @Transactional
  public void sendTradeRequest_삭제된giveItem으로거래요청시_ITEM_NOT_FOUND반환_테스트() {
    Item deletedItem = itemRepository.findAll().stream()
        .filter(Item::getIsDeleted)
        .findFirst()
        .orElse(null);

    if (deletedItem == null) {
      lineLog("삭제된 물품 없어 테스트 스킵");
      return;
    }

    // giveItem 소유자와 다른 회원의 활성 물품 탐색 (takeItem용)
    Item takeItem = itemRepository.findAll().stream()
        .filter(i -> !i.getIsDeleted()
            && !i.getMember().getMemberId().equals(deletedItem.getMember().getMemberId()))
        .findFirst()
        .orElse(null);

    if (takeItem == null) {
      lineLog("takeItem 후보 없어 테스트 스킵");
      return;
    }

    Member giveItemOwner = deletedItem.getMember();

    TradeRequest request = TradeRequest.builder()
        .member(giveItemOwner)
        .giveItemId(deletedItem.getItemId())
        .takeItemId(takeItem.getItemId())
        .build();

    CustomException ex = Assertions.assertThrows(CustomException.class,
        () -> tradeRequestService.sendTradeRequest(request));

    superLog(ex.getErrorCode());
    Assertions.assertEquals(ErrorCode.ITEM_NOT_FOUND, ex.getErrorCode(),
        "삭제된 giveItem으로 거래 요청 시 ITEM_NOT_FOUND 반환해야 함 (Bug 12 수정 검증)");
    lineLog("삭제된 giveItem 거래요청 ITEM_NOT_FOUND 검증 완료");
  }

  // Bug 12: takeItem이 softDelete된 경우 → ITEM_NOT_FOUND
  @Transactional
  public void sendTradeRequest_삭제된takeItem으로거래요청시_ITEM_NOT_FOUND반환_테스트() {
    Item deletedItem = itemRepository.findAll().stream()
        .filter(Item::getIsDeleted)
        .findFirst()
        .orElse(null);

    if (deletedItem == null) {
      lineLog("삭제된 물품 없어 테스트 스킵");
      return;
    }

    // 삭제된 물품 소유자와 다른 회원의 활성 물품 탐색 (giveItem용)
    Item giveItem = itemRepository.findAll().stream()
        .filter(i -> !i.getIsDeleted()
            && !i.getMember().getMemberId().equals(deletedItem.getMember().getMemberId()))
        .findFirst()
        .orElse(null);

    if (giveItem == null) {
      lineLog("giveItem 후보 없어 테스트 스킵");
      return;
    }

    Member giveItemOwner = giveItem.getMember();

    TradeRequest request = TradeRequest.builder()
        .member(giveItemOwner)
        .giveItemId(giveItem.getItemId())
        .takeItemId(deletedItem.getItemId())
        .build();

    CustomException ex = Assertions.assertThrows(CustomException.class,
        () -> tradeRequestService.sendTradeRequest(request));

    superLog(ex.getErrorCode());
    Assertions.assertEquals(ErrorCode.ITEM_NOT_FOUND, ex.getErrorCode(),
        "삭제된 takeItem으로 거래 요청 시 ITEM_NOT_FOUND 반환해야 함 (Bug 12 수정 검증)");
    lineLog("삭제된 takeItem 거래요청 ITEM_NOT_FOUND 검증 완료");
  }
}
