package com.romrom.report.service;

import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.member.entity.Member;
import com.romrom.member.repository.MemberRepository;
import com.romrom.report.dto.ReportRequest;
import com.romrom.report.entity.ItemReport;
import com.romrom.report.entity.MemberReport;
import com.romrom.report.enums.ItemReportReason;
import com.romrom.report.enums.MemberReportReason;
import com.romrom.report.repository.ItemReportRepository;
import com.romrom.report.repository.MemberReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.stream.Collectors;

import static com.romrom.report.entity.ItemReport.EXTRA_COMMENT_MAX_LENGTH;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

  private final ItemReportRepository itemReportRepository;
  private final MemberReportRepository memberReportRepository;
  private final ItemRepository itemRepository;
  private final MemberRepository memberRepository;

  @Transactional
  public void createReport(ReportRequest request) {

    // 기타 의견 요청 시, null 값인 기타 의견 방지
    if (request.getItemReportReasons().contains(ItemReportReason.ETC.getCode())
        && !StringUtils.hasText(request.getExtraComment())) {
      log.error("요청의 extraComment 값이 null 입니다. 유효성 검사 실패.");
      throw new CustomException(ErrorCode.NULL_EXTRA_COMMENT);
    }

    // 기타 의견 요청 시, 300자 초과한 기타 의견 방지
    if (request.getItemReportReasons().contains(ItemReportReason.ETC.getCode())
        && request.getExtraComment().length() > EXTRA_COMMENT_MAX_LENGTH) {
      log.error("요청의 extraComment 값이 {} 자 이상입니다. 유효성 검사 실패.", EXTRA_COMMENT_MAX_LENGTH  );
      throw new CustomException(ErrorCode.TOO_LONG_EXTRA_COMMENT);
    }

    Item item = itemRepository.findById(request.getItemId())
        .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));

    // 중복 신고 방지
    if (itemReportRepository.existsByItemAndMember(item, request.getMember())) {
      log.error("같은 아이템의 중복 신고는 불가능합니다.");
      throw new CustomException(ErrorCode.DUPLICATE_REPORT);
    }

    Set<ItemReportReason> itemReportReasons = request.getItemReportReasons()
        .stream()
        .map(ItemReportReason::fromCode)
        .collect(Collectors.toSet());

    ItemReport itemReport = ItemReport.builder()
        .item(item)
        .member(request.getMember())
        .itemReportReasons(itemReportReasons)
        .extraComment(request.getExtraComment())
        .build();

    itemReportRepository.save(itemReport);
  }

  @Transactional
  public void createMemberReport(ReportRequest request) {

    // 신고 사유 필수 검증
    if (request.getMemberReportReasons() == null || request.getMemberReportReasons().isEmpty()) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }

    // 기타 의견 요청 시, null 값인 기타 의견 방지
    if (request.getMemberReportReasons().contains(MemberReportReason.ETC.getCode())
        && !StringUtils.hasText(request.getExtraComment())) {
      log.error("요청의 extraComment 값이 null 입니다. 유효성 검사 실패.");
      throw new CustomException(ErrorCode.NULL_EXTRA_COMMENT);
    }

    // 기타 의견 요청 시, 1000자 초과한 기타 의견 방지
    if (request.getMemberReportReasons().contains(MemberReportReason.ETC.getCode())
        && request.getExtraComment().length() > MemberReport.EXTRA_COMMENT_MAX_LENGTH) {
      log.error("요청의 extraComment 값이 {} 자 이상입니다. 유효성 검사 실패.", MemberReport.EXTRA_COMMENT_MAX_LENGTH);
      throw new CustomException(ErrorCode.TOO_LONG_EXTRA_COMMENT);
    }

    // 신고 대상 회원 존재 여부 확인
    Member targetMember = memberRepository.findById(request.getTargetMemberId())
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

    // 자기 자신 신고 방지
    if (targetMember.getMemberId().equals(request.getMember().getMemberId())) {
      log.error("자기 자신을 신고할 수 없습니다.");
      throw new CustomException(ErrorCode.SELF_REPORT);
    }

    // 중복 신고 방지
    if (memberReportRepository.existsByTargetMemberAndReporter(targetMember, request.getMember())) {
      log.error("같은 회원의 중복 신고는 불가능합니다.");
      throw new CustomException(ErrorCode.DUPLICATE_MEMBER_REPORT);
    }

    // 신고 사유 코드를 Enum으로 변환
    Set<MemberReportReason> memberReportReasons = request.getMemberReportReasons()
        .stream()
        .map(MemberReportReason::fromCode)
        .collect(Collectors.toSet());

    MemberReport memberReport = MemberReport.builder()
        .targetMember(targetMember)
        .reporter(request.getMember())
        .memberReportReasons(memberReportReasons)
        .extraComment(request.getExtraComment())
        .build();

    memberReportRepository.save(memberReport);
  }
}
