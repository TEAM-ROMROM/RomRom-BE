package com.romrom.report.service;

import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.report.dto.ReportRequest;
import com.romrom.report.entity.ItemReport;
import com.romrom.report.enums.ItemReportReason;
import com.romrom.report.repository.ItemReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.romrom.report.entity.ItemReport.EXTRA_COMMENT_MAX_LENGTH;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

  private final ItemReportRepository itemReportRepository;
  private final ItemRepository itemRepository;

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
}
