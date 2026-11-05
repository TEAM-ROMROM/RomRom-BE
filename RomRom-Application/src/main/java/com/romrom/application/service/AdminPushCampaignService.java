package com.romrom.application.service;

import com.romrom.application.dto.AdminPushCampaignRequest;
import com.romrom.application.dto.AdminPushCampaignResponse;
import com.romrom.application.util.PushCampaignInputRule;
import com.romrom.common.constant.PushCampaignPurpose;
import com.romrom.common.constant.PushCampaignStatus;
import com.romrom.common.constant.PushCampaignTargetType;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.notification.campaign.AdvertisementPushRule;
import com.romrom.notification.campaign.PushCampaignDispatcher;
import com.romrom.notification.campaign.PushCampaignTargetFilter;
import com.romrom.notification.campaign.PushCampaignTargetQuery;
import com.romrom.notification.campaign.PushCampaignTargetQueryBuilder;
import com.romrom.notification.campaign.PushCampaignTargetReader;
import com.romrom.notification.entity.PushCampaign;
import com.romrom.notification.repository.PushCampaignRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * 관리자 푸시 캠페인 (#874): 미리보기(dry run), 테스트 발송, 즉시·예약 발송, 목록, 상세, 예약 취소
 * 생성은 저장 커밋 후 실행 (트랜잭션 없음: 실행기가 커밋된 행을 읽음)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPushCampaignService {

  private final PushCampaignRepository pushCampaignRepository;
  private final PushCampaignTargetReader pushCampaignTargetReader;
  private final PushCampaignDispatcher pushCampaignDispatcher;

  /**
   * 미리보기: 입력 검증 > 대상·수신 동의 회원 수, 토큰 수, 표시 문구 (발송 없음)
   */
  public AdminPushCampaignResponse previewCampaign(AdminPushCampaignRequest request) {
    PushCampaign draftCampaign = toDraftCampaign(request, resolveTargetType(request, false));
    PushCampaignTargetQuery targetQuery = PushCampaignTargetQueryBuilder.build(draftCampaign.getPurpose(),
        draftCampaign.getTargetType(), PushCampaignTargetFilter.fromJson(draftCampaign.getTargetFilter()),
        LocalDateTime.now());
    return AdminPushCampaignResponse.builder()
        .targetMemberCount(pushCampaignTargetReader.countTargetMembers(targetQuery))
        .pushConsentedMemberCount(pushCampaignTargetReader.countPushConsentedMembers(targetQuery))
        .pushTokenCount(pushCampaignTargetReader.countPushConsentedTokens(targetQuery))
        .displayTitle(AdvertisementPushRule.toDisplayTitle(draftCampaign.getPurpose(), draftCampaign.getTitle()))
        .displayBody(AdvertisementPushRule.toDisplayBody(draftCampaign.getPurpose(), draftCampaign.getBody()))
        .build();
  }

  /**
   * 테스트 발송: 관리자 본인 토큰 확인 > TEST 캠페인 저장 > 바로 실행 > 결과 포함 캠페인 (야간 제한 없음)
   */
  public AdminPushCampaignResponse sendTestCampaign(AdminPushCampaignRequest request) {
    PushCampaign testCampaign = toDraftCampaign(request, PushCampaignTargetType.TEST);
    PushCampaignTargetQuery targetQuery = PushCampaignTargetQueryBuilder.build(testCampaign.getPurpose(),
        PushCampaignTargetType.TEST, PushCampaignTargetFilter.fromJson(testCampaign.getTargetFilter()), LocalDateTime.now());
    if (pushCampaignTargetReader.countPushConsentedTokens(targetQuery) == 0) {
      log.warn("[push campaign] test rejected 400 : admin has no fcm token, adminId={}", request.getExecutorAdminId());
      throw new CustomException(ErrorCode.PUSH_TEST_TOKEN_NOT_FOUND);
    }
    testCampaign.setScheduledAt(LocalDateTime.now());
    PushCampaign savedCampaign = pushCampaignRepository.save(testCampaign);
    pushCampaignDispatcher.claimAndRunNow(savedCampaign.getPushCampaignId());
    return AdminPushCampaignResponse.builder().pushCampaign(findCampaign(savedCampaign.getPushCampaignId())).build();
  }

  /**
   * 즉시·예약 발송: 검증(예약 미래·광고 야간) > SCHEDULED 저장 > 즉시면 선점 후 비동기 실행 (예약은 1분 스케줄러)
   */
  public AdminPushCampaignResponse createCampaign(AdminPushCampaignRequest request) {
    PushCampaign pushCampaign = toDraftCampaign(request, resolveTargetType(request, true));
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime scheduledAt = PushCampaignInputRule.toValidatedScheduledAt(request.getScheduledAt(), now);
    boolean isImmediateSend = scheduledAt == null;
    PushCampaignInputRule.validateAdvertisementSendTime(pushCampaign.getPurpose(),
        isImmediateSend ? AdvertisementPushRule.nowInKorea() : scheduledAt);
    pushCampaign.setScheduledAt(isImmediateSend ? now : scheduledAt);

    PushCampaign savedCampaign = pushCampaignRepository.save(pushCampaign);
    log.info("[push campaign] created : pushCampaignId={}, purpose={}, targetType={}, scheduledAt={}, adminId={}",
        savedCampaign.getPushCampaignId(), savedCampaign.getPurpose(), savedCampaign.getTargetType(),
        savedCampaign.getScheduledAt(), request.getExecutorAdminId());
    if (isImmediateSend) {
      pushCampaignDispatcher.claimAndDispatch(savedCampaign.getPushCampaignId());
    }
    return AdminPushCampaignResponse.builder().pushCampaign(findCampaign(savedCampaign.getPushCampaignId())).build();
  }

  /**
   * 회원 상세 "알림 보내기": 회원 1명 MEMBERS 캠페인 즉시 발송 (목적·딥링크 선택)
   */
  public PushCampaign sendMemberCampaign(UUID targetMemberId, String title, String body, PushCampaignPurpose purpose,
      String deepLink, UUID executorAdminId) {
    AdminPushCampaignRequest memberCampaignRequest = AdminPushCampaignRequest.builder()
        .pushTitle(title)
        .pushBody(body)
        .pushDeepLink(deepLink)
        .purpose(purpose)
        .targetType(PushCampaignTargetType.MEMBERS)
        .memberIdentifiers(targetMemberId.toString())
        .executorAdminId(executorAdminId)
        .build();
    return createCampaign(memberCampaignRequest).getPushCampaign();
  }

  public AdminPushCampaignResponse getCampaigns(AdminPushCampaignRequest request) {
    int pageNumber = request.getPageNumber() == null ? 0 : Math.max(request.getPageNumber(), 0);
    int pageSize = request.getPageSize() == null ? 20 : Math.min(Math.max(request.getPageSize(), 1), 100);
    Page<PushCampaign> pushCampaignPage = pushCampaignRepository.findAllByOrderByCreatedDateDesc(
        PageRequest.of(pageNumber, pageSize));
    return AdminPushCampaignResponse.builder()
        .pushCampaigns(pushCampaignPage.getContent())
        .totalPages(pushCampaignPage.getTotalPages())
        .totalElements(pushCampaignPage.getTotalElements())
        .currentPage(pushCampaignPage.getNumber())
        .build();
  }

  public AdminPushCampaignResponse getCampaignDetail(AdminPushCampaignRequest request) {
    return AdminPushCampaignResponse.builder().pushCampaign(findCampaign(request.getPushCampaignId())).build();
  }

  /**
   * 예약 취소: SCHEDULED 일 때만 (조건부 UPDATE, 이미 시작·완료 > PUSH_CAMPAIGN_NOT_CANCELABLE)
   */
  public AdminPushCampaignResponse cancelCampaign(AdminPushCampaignRequest request) {
    PushCampaign pushCampaign = findCampaign(request.getPushCampaignId());
    int canceledCount = pushCampaignRepository.cancelScheduledCampaign(pushCampaign.getPushCampaignId(), LocalDateTime.now());
    if (canceledCount == 0) {
      log.warn("[push campaign] cancel rejected 400 : status={}, pushCampaignId={}", pushCampaign.getStatus(),
          pushCampaign.getPushCampaignId());
      throw new CustomException(ErrorCode.PUSH_CAMPAIGN_NOT_CANCELABLE);
    }
    log.info("[push campaign] canceled : pushCampaignId={}, adminId={}", pushCampaign.getPushCampaignId(),
        request.getExecutorAdminId());
    return AdminPushCampaignResponse.builder().pushCampaign(findCampaign(pushCampaign.getPushCampaignId())).build();
  }

  // 입력 검증 > 저장 전 캠페인 (상태 SCHEDULED, 예약 시각은 호출부에서 설정)
  private PushCampaign toDraftCampaign(AdminPushCampaignRequest request, PushCampaignTargetType targetType) {
    PushCampaignPurpose purpose = request.getPurpose() == null ? PushCampaignPurpose.INFORMATION : request.getPurpose();
    return PushCampaign.builder()
        .title(PushCampaignInputRule.toValidatedText(request.getPushTitle(), PushCampaign.TITLE_MAXIMUM_LENGTH))
        .body(PushCampaignInputRule.toValidatedText(request.getPushBody(), PushCampaign.BODY_MAXIMUM_LENGTH))
        .imageUrl(PushCampaignInputRule.toValidatedImageUrl(request.getPushImageUrl()))
        .deepLink(PushCampaignInputRule.toValidatedDeepLink(request.getPushDeepLink()))
        .purpose(purpose)
        .targetType(targetType)
        .targetFilter(PushCampaignInputRule.toTargetFilter(targetType, request).toJson())
        .status(PushCampaignStatus.SCHEDULED)
        .createdByAdminId(request.getExecutorAdminId())
        .build();
  }

  // 대상 종류 (미입력 > 400, 실제 발송에 TEST 불가 > 테스트 발송 API 사용)
  private PushCampaignTargetType resolveTargetType(AdminPushCampaignRequest request, boolean isRealSend) {
    PushCampaignTargetType targetType = request.getTargetType();
    if (targetType == null || (isRealSend && targetType == PushCampaignTargetType.TEST)) {
      log.warn("[push campaign] rejected 400 : invalid targetType={}", targetType);
      throw new CustomException(ErrorCode.INVALID_PUSH_CAMPAIGN_TARGET);
    }
    return targetType;
  }

  private PushCampaign findCampaign(UUID pushCampaignId) {
    if (pushCampaignId == null) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    return pushCampaignRepository.findById(pushCampaignId)
        .orElseThrow(() -> new CustomException(ErrorCode.PUSH_CAMPAIGN_NOT_FOUND));
  }
}
