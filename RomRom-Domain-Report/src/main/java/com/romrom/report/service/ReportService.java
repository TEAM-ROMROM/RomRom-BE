package com.romrom.report.service;

import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.member.entity.Member;
import com.romrom.member.repository.MemberRepository;
import com.romrom.report.dto.ReportRequest;
import com.romrom.report.entity.Report;
import com.romrom.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.romrom.report.entity.Report.EXTRA_COMMENT_MAX_LENGTH;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

  private final ReportRepository reportRepository;
  private final ItemRepository itemRepository;
  private final MemberRepository memberRepository;

  @Transactional
  public void createReport(ReportRequest request) {

    // null 값인 기타 의견 방지
    if (request.getExtraComment() == null) {
      log.error("요청의 extraComment 값이 null 입니다. 유효성 검사 실패.");
      throw new CustomException(ErrorCode.TOO_LONG_EXTRA_COMMENT);
    }

    // 300자 초과한 기타 의견 방지
    if (request.getExtraComment().length() > EXTRA_COMMENT_MAX_LENGTH) {
      log.error("요청의 extraComment 값이 {} 자 이상입니다. 유효성 검사 실패.", EXTRA_COMMENT_MAX_LENGTH  );
      throw new CustomException(ErrorCode.TOO_LONG_EXTRA_COMMENT);
    }

    Item item = itemRepository.findById(request.getItemId())
        .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));
    Member reporter = memberRepository.getReferenceById(request.getMember().getMemberId());

    // 중복 신고 방지
    if (reportRepository.existsByItemAndMember(item, reporter)) {
      log.error("같은 아이템의 중복 신고는 불가능합니다.");
      throw new CustomException(ErrorCode.DUPLICATE_REPORT);
    }

    Report report = Report.builder()
        .item(item)
        .member(reporter)
        .reportReasons(request.getReasons())
        .extraComment(request.getExtraComment())
        .build();

    reportRepository.save(report);
  }
}
