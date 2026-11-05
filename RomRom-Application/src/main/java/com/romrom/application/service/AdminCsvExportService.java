package com.romrom.application.service;

import static com.romrom.application.util.AdminCsvWriter.toCsvDateTime;
import static com.romrom.application.util.AdminCsvWriter.toCsvText;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.util.AdminCsvWriter;
import com.romrom.application.util.AdminDateRangeParser;
import com.romrom.common.constant.AccountStatus;
import com.romrom.common.constant.ItemCategory;
import com.romrom.common.constant.ItemCondition;
import com.romrom.common.constant.ItemStatus;
import com.romrom.common.constant.SocialPlatform;
import com.romrom.common.util.LogMaskUtil;
import com.romrom.item.entity.postgres.Item;
import com.romrom.member.entity.Member;
import com.romrom.report.entity.ChatMessageReport;
import com.romrom.report.entity.ItemReport;
import com.romrom.report.entity.MemberReport;
import com.romrom.report.enums.ItemReportReason;
import com.romrom.report.enums.MemberReportReason;
import com.romrom.report.enums.ReportStatus;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 CSV 내보내기 (#874): 회원·물품·신고 3종(물품·회원·채팅) > 목록 화면과 같은 필터, 1000행 묶음 조회, 최대 10,000행
 * 개인정보 최소화: 이메일 마스킹, 전화번호·위치 없음
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCsvExportService {

  // 내보내기 최대 행 수
  public static final int MAXIMUM_EXPORT_ROW_COUNT = 10_000;
  // 묶음 조회 크기
  static final int EXPORT_CHUNK_SIZE = 1_000;
  // 파일명: romrom_{대상}_{yyyyMMdd_HHmm}.csv
  private static final DateTimeFormatter FILE_NAME_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");
  private static final String CSV_CONTENT_TYPE = "text/csv; charset=UTF-8";
  private static final String REASON_DELIMITER = " / ";

  private final AdminMemberQueryService adminMemberQueryService;
  private final AdminItemService adminItemService;
  private final AdminReportService adminReportService;
  private final AdminChatMessageReportService adminChatMessageReportService;
  private final EntityManager entityManager;

  @Transactional(readOnly = true)
  public void exportMembers(AdminRequest request, HttpServletResponse response) throws IOException {
    exportRows("members", request, response,
        List.of("회원 ID", "닉네임", "이메일(마스킹)", "가입 경로", "계정 상태", "마케팅 수신 동의", "받은 좋아요", "가입일", "마지막 활동", "정지 종료"),
        chunkNumber -> adminMemberQueryService.getMembersForAdmin(withChunkPage(request, chunkNumber)).getMembers().getContent(),
        member -> List.of(
            toCsvText(member.getMemberId()),
            toCsvText(member.getNickname()),
            toCsvText(LogMaskUtil.maskEmail(member.getEmail())),
            describe(member.getSocialPlatform(), SocialPlatform::getDescription),
            describe(member.getAccountStatus(), AccountStatus::getDescription),
            toCsvText(member.getIsMarketingInfoAgreed()),
            toCsvText(member.getTotalLikeCount()),
            toCsvDateTime(member.getCreatedDate()),
            toCsvDateTime(member.getLastActiveAt()),
            toCsvDateTime(member.getSuspendedUntil())));
  }

  @Transactional(readOnly = true)
  public void exportItems(AdminRequest request, HttpServletResponse response) throws IOException {
    // 기간 형식 오류는 파일 응답 시작 전에 400
    AdminDateRangeParser.parseStartOfDay(request.getStartDate());
    AdminDateRangeParser.parseEndOfDay(request.getEndDate());
    exportRows("items", request, response,
        List.of("물품 ID", "물품명", "카테고리", "상태", "거래 상태", "가격", "좋아요", "관리자 숨김", "등록자 ID", "등록자 닉네임", "등록일"),
        chunkNumber -> adminItemService.getItemsForAdmin(withChunkPage(request, chunkNumber)).getItems().getContent(),
        item -> List.of(
            toCsvText(item.getItemId()),
            toCsvText(item.getItemName()),
            describe(item.getItemCategory(), ItemCategory::getDescription),
            describe(item.getItemCondition(), ItemCondition::getDescription),
            describe(item.getItemStatus(), ItemStatus::getDescription),
            toCsvText(item.getPrice()),
            toCsvText(item.getLikeCount()),
            toCsvText(item.getIsAdminHidden()),
            memberIdOf(item.getMember()),
            nicknameOf(item.getMember()),
            toCsvDateTime(item.getCreatedDate())));
  }

  @Transactional(readOnly = true)
  public void exportItemReports(AdminRequest request, HttpServletResponse response) throws IOException {
    exportRows("item_reports", request, response,
        List.of("신고 ID", "처리 상태", "사유", "추가 설명", "물품 ID", "물품명", "물품 등록자 닉네임", "신고자 닉네임", "접수일"),
        chunkNumber -> adminReportService.getItemReports(withChunkPage(request, chunkNumber)).getItemReports(),
        itemReport -> List.of(
            toCsvText(itemReport.getItemReportId()),
            describe(itemReport.getStatus(), ReportStatus::getDescription),
            joinDescriptions(itemReport.getItemReportReasons(), ItemReportReason::getDescription),
            toCsvText(itemReport.getExtraComment()),
            itemReport.getItem() != null ? toCsvText(itemReport.getItem().getItemId()) : "",
            itemReport.getItem() != null ? toCsvText(itemReport.getItem().getItemName()) : "",
            itemReport.getItem() != null ? nicknameOf(itemReport.getItem().getMember()) : "",
            nicknameOf(itemReport.getMember()),
            toCsvDateTime(itemReport.getCreatedDate())));
  }

  @Transactional(readOnly = true)
  public void exportMemberReports(AdminRequest request, HttpServletResponse response) throws IOException {
    exportRows("member_reports", request, response,
        List.of("신고 ID", "처리 상태", "사유", "추가 설명", "신고 대상 ID", "신고 대상 닉네임", "신고자 ID", "신고자 닉네임", "접수일"),
        chunkNumber -> adminReportService.getMemberReports(withChunkPage(request, chunkNumber)).getMemberReports(),
        memberReport -> List.of(
            toCsvText(memberReport.getMemberReportId()),
            describe(memberReport.getStatus(), ReportStatus::getDescription),
            joinDescriptions(memberReport.getMemberReportReasons(), MemberReportReason::getDescription),
            toCsvText(memberReport.getExtraComment()),
            memberIdOf(memberReport.getTargetMember()),
            nicknameOf(memberReport.getTargetMember()),
            memberIdOf(memberReport.getReporter()),
            nicknameOf(memberReport.getReporter()),
            toCsvDateTime(memberReport.getCreatedDate())));
  }

  @Transactional(readOnly = true)
  public void exportChatMessageReports(AdminRequest request, HttpServletResponse response) throws IOException {
    exportRows("chat_message_reports", request, response,
        List.of("신고 ID", "처리 상태", "사유", "추가 설명", "채팅방 ID", "메시지 ID", "메시지 내용", "신고 대상 ID", "신고 대상 닉네임", "신고자 닉네임", "접수일"),
        chunkNumber -> adminChatMessageReportService.getChatMessageReports(withChunkPage(request, chunkNumber)).getChatMessageReports(),
        chatMessageReport -> List.of(
            toCsvText(chatMessageReport.getChatMessageReportId()),
            describe(chatMessageReport.getStatus(), ReportStatus::getDescription),
            joinDescriptions(chatMessageReport.getReasons(), MemberReportReason::getDescription),
            toCsvText(chatMessageReport.getExtraComment()),
            toCsvText(chatMessageReport.getChatRoomId()),
            toCsvText(chatMessageReport.getChatMessageId()),
            toCsvText(chatMessageReport.getMessageContentSnapshot()),
            memberIdOf(chatMessageReport.getTargetMember()),
            nicknameOf(chatMessageReport.getTargetMember()),
            nicknameOf(chatMessageReport.getReporter()),
            toCsvDateTime(chatMessageReport.getCreatedDate())));
  }

  /**
   * 내보내기 이름 > 파일명 (romrom_members_20261105_1730.csv)
   */
  public static String buildFileName(String exportName, LocalDateTime exportedAt) {
    return "romrom_" + exportName + "_" + exportedAt.format(FILE_NAME_TIME_FORMAT) + ".csv";
  }

  // 응답 헤더 > 묶음 조회·쓰기 (묶음마다 영속성 컨텍스트 비움, 메모리 일정)
  private <T> void exportRows(String exportName, AdminRequest request, HttpServletResponse response,
      List<String> headerCells, IntFunction<List<T>> chunkReader, Function<T, List<String>> rowConverter)
      throws IOException {
    response.setContentType(CSV_CONTENT_TYPE);
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
        "attachment; filename=\"" + buildFileName(exportName, LocalDateTime.now()) + "\"");
    Writer csvWriter = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8));
    int writtenRowCount = AdminCsvWriter.writeAllRows(csvWriter, headerCells, chunkReader, rowConverter,
        EXPORT_CHUNK_SIZE, MAXIMUM_EXPORT_ROW_COUNT, entityManager::clear);
    log.info("[admin csv export] completed : export={}, rows={}, sortBy={}", exportName, writtenRowCount, request.getSortBy());
  }

  // 같은 필터 요청에 묶음 페이지만 바꿈
  private static AdminRequest withChunkPage(AdminRequest request, int chunkNumber) {
    request.setPageNumber(chunkNumber);
    request.setPageSize(EXPORT_CHUNK_SIZE);
    return request;
  }

  private static <E> String describe(E enumValue, Function<E, String> toDescription) {
    return enumValue == null ? "" : toDescription.apply(enumValue);
  }

  private static <E> String joinDescriptions(Collection<E> enumValues, Function<E, String> toDescription) {
    if (enumValues == null || enumValues.isEmpty()) {
      return "";
    }
    return enumValues.stream().map(toDescription).collect(Collectors.joining(REASON_DELIMITER));
  }

  private static String memberIdOf(Member member) {
    return member == null ? "" : toCsvText(member.getMemberId());
  }

  private static String nicknameOf(Member member) {
    return member == null ? "" : toCsvText(member.getNickname());
  }
}
