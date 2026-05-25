package com.romrom.application.service;

import static me.suhsaechan.suhlogger.util.SuhLogger.lineLog;
import static me.suhsaechan.suhlogger.util.SuhLogger.superLog;
import static me.suhsaechan.suhlogger.util.SuhLogger.timeLog;

import com.romrom.application.dto.AdminRequest;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.report.enums.ReportType;
import com.romrom.web.RomBackApplication;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = RomBackApplication.class)
@ActiveProfiles("dev")
@Slf4j
class AdminReportServiceTest {

  @Autowired
  AdminReportService adminReportService;

  @Test
  public void mainTest() {
    lineLog("테스트시작");

    lineLog(null);
    timeLog(this::getItemReportDetail_존재하지않는신고_REPORT_NOT_FOUND반환_테스트);
    lineLog(null);
    timeLog(this::getMemberReportDetail_존재하지않는신고_REPORT_NOT_FOUND반환_테스트);
    lineLog(null);
    timeLog(this::updateStatus_존재하지않는itemReportId_REPORT_NOT_FOUND반환_테스트);
    lineLog(null);
    timeLog(this::updateStatus_존재하지않는memberReportId_REPORT_NOT_FOUND반환_테스트);
    lineLog(null);

    lineLog("테스트종료");
  }

  // Bug 7: getItemReportDetail - INVALID_REQUEST → REPORT_NOT_FOUND
  public void getItemReportDetail_존재하지않는신고_REPORT_NOT_FOUND반환_테스트() {
    UUID nonExistentId = UUID.randomUUID();
    AdminRequest request = AdminRequest.builder()
        .reportId(nonExistentId)
        .build();

    CustomException ex = Assertions.assertThrows(CustomException.class,
        () -> adminReportService.getItemReportDetail(request));

    superLog(ex.getErrorCode());
    Assertions.assertEquals(ErrorCode.REPORT_NOT_FOUND, ex.getErrorCode(),
        "존재하지 않는 신고 조회 시 REPORT_NOT_FOUND 반환해야 함");
    lineLog("getItemReportDetail REPORT_NOT_FOUND 검증 완료");
  }

  // Bug 7: getMemberReportDetail - INVALID_REQUEST → REPORT_NOT_FOUND
  public void getMemberReportDetail_존재하지않는신고_REPORT_NOT_FOUND반환_테스트() {
    UUID nonExistentId = UUID.randomUUID();
    AdminRequest request = AdminRequest.builder()
        .reportId(nonExistentId)
        .build();

    CustomException ex = Assertions.assertThrows(CustomException.class,
        () -> adminReportService.getMemberReportDetail(request));

    superLog(ex.getErrorCode());
    Assertions.assertEquals(ErrorCode.REPORT_NOT_FOUND, ex.getErrorCode(),
        "존재하지 않는 신고 조회 시 REPORT_NOT_FOUND 반환해야 함");
    lineLog("getMemberReportDetail REPORT_NOT_FOUND 검증 완료");
  }

  // Bug 9: updateStatus - ITEM 타입, 존재하지 않는 reportId → REPORT_NOT_FOUND
  public void updateStatus_존재하지않는itemReportId_REPORT_NOT_FOUND반환_테스트() {
    UUID nonExistentId = UUID.randomUUID();
    AdminRequest request = AdminRequest.builder()
        .reportId(nonExistentId)
        .reportType(ReportType.ITEM)
        .build();

    CustomException ex = Assertions.assertThrows(CustomException.class,
        () -> adminReportService.updateStatus(request));

    superLog(ex.getErrorCode());
    Assertions.assertEquals(ErrorCode.REPORT_NOT_FOUND, ex.getErrorCode(),
        "존재하지 않는 물품신고 상태변경 시 REPORT_NOT_FOUND 반환해야 함");
    lineLog("updateStatus ITEM REPORT_NOT_FOUND 검증 완료");
  }

  // Bug 9: updateStatus - MEMBER 타입, 존재하지 않는 reportId → REPORT_NOT_FOUND
  public void updateStatus_존재하지않는memberReportId_REPORT_NOT_FOUND반환_테스트() {
    UUID nonExistentId = UUID.randomUUID();
    AdminRequest request = AdminRequest.builder()
        .reportId(nonExistentId)
        .reportType(ReportType.MEMBER)
        .build();

    CustomException ex = Assertions.assertThrows(CustomException.class,
        () -> adminReportService.updateStatus(request));

    superLog(ex.getErrorCode());
    Assertions.assertEquals(ErrorCode.REPORT_NOT_FOUND, ex.getErrorCode(),
        "존재하지 않는 회원신고 상태변경 시 REPORT_NOT_FOUND 반환해야 함");
    lineLog("updateStatus MEMBER REPORT_NOT_FOUND 검증 완료");
  }
}
