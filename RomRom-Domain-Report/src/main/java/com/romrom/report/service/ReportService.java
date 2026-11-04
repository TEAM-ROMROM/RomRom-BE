package com.romrom.report.service;

import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.member.entity.Member;
import com.romrom.member.repository.MemberRepository;
import com.romrom.report.dto.ReportRequest;
import com.romrom.report.entity.ChatMessageReport;
import com.romrom.report.entity.ItemReport;
import com.romrom.report.entity.MemberReport;
import com.romrom.report.enums.ItemReportReason;
import com.romrom.report.enums.MemberReportReason;
import com.romrom.report.repository.ChatMessageReportRepository;
import com.romrom.report.repository.ItemReportRepository;
import com.romrom.report.repository.MemberReportRepository;
import com.romrom.report.event.ReportAlertEvent;
import com.romrom.report.enums.ReportType;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import static com.romrom.report.entity.ItemReport.EXTRA_COMMENT_MAX_LENGTH;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

  private final ItemReportRepository itemReportRepository;
  private final MemberReportRepository memberReportRepository;
  private final ChatMessageReportRepository chatMessageReportRepository;
  private final ItemRepository itemRepository;
  private final MemberRepository memberRepository;
  private final ApplicationEventPublisher applicationEventPublisher;

  @Transactional
  public void createReport(ReportRequest request) {

    // 기타 의견 검증 (기타 사유면 필수, 300자 이하)
    validateExtraComment(request.getItemReportReasons().contains(ItemReportReason.ETC.getCode()),
        request.getExtraComment(), EXTRA_COMMENT_MAX_LENGTH);

    Item item = itemRepository.findByItemIdAndIsDeletedFalse(request.getItemId())
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

    String itemReportReasonDescriptions = itemReportReasons.stream()
        .map(ItemReportReason::getDescription)
        .collect(Collectors.joining(", "));

    applicationEventPublisher.publishEvent(ReportAlertEvent.builder()
        .reportType(ReportType.ITEM)
        .targetId(item.getItemId())
        .targetName(item.getItemName())
        .reportReasons(itemReportReasonDescriptions)
        .extraComment(request.getExtraComment())
        .build());
  }

  @Transactional
  public void createMemberReport(ReportRequest request) {

    // 신고 사유 필수 검증
    if (request.getMemberReportReasons() == null || request.getMemberReportReasons().isEmpty()) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }

    // 기타 의견 검증 (기타 사유면 필수, 1000자 이하)
    validateExtraComment(request.getMemberReportReasons().contains(MemberReportReason.ETC.getCode()),
        request.getExtraComment(), MemberReport.EXTRA_COMMENT_MAX_LENGTH);

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

    String memberReportReasonDescriptions = memberReportReasons.stream()
        .map(MemberReportReason::getDescription)
        .collect(Collectors.joining(", "));

    applicationEventPublisher.publishEvent(ReportAlertEvent.builder()
        .reportType(ReportType.MEMBER)
        .targetId(targetMember.getMemberId())
        .targetName(targetMember.getNickname())
        .reportReasons(memberReportReasonDescriptions)
        .extraComment(request.getExtraComment())
        .build());
  }

  /**
   * 채팅 메시지 신고 저장: 사유 검증 > 기타 의견 검증 > 자기 신고·중복 차단 > 저장 > 관리자 알림 이벤트
   * 방 멤버·메시지 소속 확인은 호출부(채팅 메시지 신고 응용 서비스) 책임
   */
  @Transactional
  public void createChatMessageReport(ReportRequest request, UUID targetMemberId, String messageContentSnapshot) {
    if (request.getMemberReportReasons() == null || request.getMemberReportReasons().isEmpty()) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    validateExtraComment(request.getMemberReportReasons().contains(MemberReportReason.ETC.getCode()),
        request.getExtraComment(), ChatMessageReport.EXTRA_COMMENT_MAX_LENGTH);

    Member reporter = request.getMember();
    if (reporter.getMemberId().equals(targetMemberId)) {
      throw new CustomException(ErrorCode.SELF_REPORT);
    }
    if (chatMessageReportRepository.existsByChatMessageIdAndReporterMemberId(request.getChatMessageId(),
        reporter.getMemberId())) {
      throw new CustomException(ErrorCode.DUPLICATE_CHAT_MESSAGE_REPORT);
    }
    Member targetMember = memberRepository.findById(targetMemberId)
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

    Set<MemberReportReason> reasons = toMemberReportReasons(request.getMemberReportReasons());
    chatMessageReportRepository.save(ChatMessageReport.builder()
        .reporter(reporter)
        .targetMember(targetMember)
        .chatRoomId(request.getChatRoomId())
        .chatMessageId(request.getChatMessageId())
        .messageContentSnapshot(truncate(messageContentSnapshot, ChatMessageReport.MESSAGE_CONTENT_SNAPSHOT_MAX_LENGTH))
        .reasons(reasons)
        .extraComment(request.getExtraComment())
        .build());

    applicationEventPublisher.publishEvent(ReportAlertEvent.builder()
        .reportType(ReportType.CHAT_MESSAGE)
        .targetId(targetMember.getMemberId())
        .targetName(targetMember.getNickname())
        .reportReasons(reasons.stream().map(MemberReportReason::getDescription).collect(Collectors.joining(", ")))
        .extraComment(request.getExtraComment())
        .build());
  }

  /**
   * 기타 의견 검증: 기타 사유 선택 시 필수 + 최대 길이 (물품·회원·채팅 메시지 신고 공통)
   */
  private void validateExtraComment(boolean hasEtcReason, String extraComment, int extraCommentMaxLength) {
    if (!hasEtcReason) {
      return;
    }
    if (!StringUtils.hasText(extraComment)) {
      log.error("요청의 extraComment 값이 null 입니다. 유효성 검사 실패.");
      throw new CustomException(ErrorCode.NULL_EXTRA_COMMENT);
    }
    if (extraComment.length() > extraCommentMaxLength) {
      log.error("요청의 extraComment 값이 {} 자 이상입니다. 유효성 검사 실패.", extraCommentMaxLength);
      throw new CustomException(ErrorCode.TOO_LONG_EXTRA_COMMENT);
    }
  }

  // 사유 코드 > 회원 신고 사유 (모르는 코드 > 잘못된 요청)
  private Set<MemberReportReason> toMemberReportReasons(Set<Integer> reasonCodes) {
    try {
      return reasonCodes.stream().map(MemberReportReason::fromCode).collect(Collectors.toSet());
    } catch (IllegalArgumentException invalidReasonCode) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
  }

  private String truncate(String text, int maximumLength) {
    if (text == null || text.length() <= maximumLength) {
      return text;
    }
    return text.substring(0, maximumLength);
  }

  // 물품 상세용: 단일 물품 신고 여부
  @Transactional(readOnly = true)
  public boolean isItemReportedByMember(UUID itemId, UUID memberId) {
    return itemReportRepository.existsByItemItemIdAndMemberMemberId(itemId, memberId);
  }

  // 물품 리스트용: 배치 물품 신고 여부
  @Transactional(readOnly = true)
  public Set<UUID> getReportedItemIds(UUID memberId, Collection<UUID> itemIds) {
    if (itemIds.isEmpty()) {
      return Set.of();
    }
    return itemReportRepository.findReportedItemIdsByMemberIdAndItemIds(memberId, itemIds);
  }

  /**
   * 회원이 신고한 물품 ID 전체 (물품 검색 결과 제외용, #862)
   */
  @Transactional(readOnly = true)
  public Set<UUID> getAllReportedItemIds(UUID memberId) {
    return itemReportRepository.findAllReportedItemIdsByMemberId(memberId);
  }
}
