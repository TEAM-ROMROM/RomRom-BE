package com.romrom.report.service;

import static me.suhsaechan.suhlogger.util.SuhLogger.lineLog;
import static me.suhsaechan.suhlogger.util.SuhLogger.superLog;
import static me.suhsaechan.suhlogger.util.SuhLogger.timeLog;

import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.member.entity.Member;
import com.romrom.member.repository.MemberRepository;
import com.romrom.report.dto.ReportRequest;
import com.romrom.report.enums.ItemReportReason;
import com.romrom.web.RomBackApplication;
import java.util.Set;
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
class ReportServiceTest {

  @Autowired
  ReportService reportService;

  @Autowired
  ItemRepository itemRepository;

  @Autowired
  MemberRepository memberRepository;

  @Test
  public void mainTest() {
    lineLog("테스트시작");

    lineLog(null);
    timeLog(this::createReport_존재하지않는물품신고시_ITEM_NOT_FOUND반환_테스트);
    lineLog(null);
    timeLog(this::createReport_삭제된물품신고시_ITEM_NOT_FOUND반환_테스트);
    lineLog(null);

    lineLog("테스트종료");
  }

  // Bug 10: 존재하지 않는 itemId로 신고 시도 → ITEM_NOT_FOUND
  public void createReport_존재하지않는물품신고시_ITEM_NOT_FOUND반환_테스트() {
    Member reporter = memberRepository.findAll().stream()
        .filter(m -> !m.getIsDeleted())
        .findFirst()
        .orElseThrow(() -> new RuntimeException("테스트용 활성 회원이 없습니다"));

    ReportRequest request = ReportRequest.builder()
        .member(reporter)
        .itemId(java.util.UUID.randomUUID())
        .itemReportReasons(Set.of(ItemReportReason.FRAUD.getCode()))
        .build();

    CustomException ex = Assertions.assertThrows(CustomException.class,
        () -> reportService.createReport(request));

    superLog(ex.getErrorCode());
    Assertions.assertEquals(ErrorCode.ITEM_NOT_FOUND, ex.getErrorCode(),
        "존재하지 않는 물품 신고 시 ITEM_NOT_FOUND 반환해야 함");
    lineLog("존재하지 않는 물품 신고 ITEM_NOT_FOUND 검증 완료");
  }

  // Bug 10: isDeleted=true인 물품 신고 시도 → ITEM_NOT_FOUND
  @Transactional
  public void createReport_삭제된물품신고시_ITEM_NOT_FOUND반환_테스트() {
    // isDeleted=true인 물품 조회
    Item deletedItem = itemRepository.findAll().stream()
        .filter(Item::getIsDeleted)
        .findFirst()
        .orElse(null);

    if (deletedItem == null) {
      lineLog("삭제된 물품이 없어 테스트 스킵");
      return;
    }

    Member reporter = memberRepository.findAll().stream()
        .filter(m -> !m.getIsDeleted()
            && !m.getMemberId().equals(deletedItem.getMember().getMemberId()))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("테스트용 활성 회원이 없습니다"));

    ReportRequest request = ReportRequest.builder()
        .member(reporter)
        .itemId(deletedItem.getItemId())
        .itemReportReasons(Set.of(ItemReportReason.FRAUD.getCode()))
        .build();

    CustomException ex = Assertions.assertThrows(CustomException.class,
        () -> reportService.createReport(request));

    superLog(ex.getErrorCode());
    Assertions.assertEquals(ErrorCode.ITEM_NOT_FOUND, ex.getErrorCode(),
        "삭제된 물품 신고 시 ITEM_NOT_FOUND 반환해야 함");
    lineLog("삭제된 물품 신고 ITEM_NOT_FOUND 검증 완료");
  }
}
