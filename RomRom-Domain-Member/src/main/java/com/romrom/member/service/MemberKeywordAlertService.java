package com.romrom.member.service;

import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.member.dto.MemberKeywordAlertRequest;
import com.romrom.member.dto.MemberKeywordAlertResponse;
import com.romrom.member.entity.MemberKeywordAlert;
import com.romrom.member.repository.MemberKeywordAlertRepository;
import com.romrom.member.util.KeywordAlertRule;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 키워드 알림 관리 (#873): 목록 조회, 등록, 삭제 (응답은 모두 최신 목록)
 * 등록 검증 3개: 길이(2~20자) > 중복 > 개수(최대 10개)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemberKeywordAlertService {

  private final MemberKeywordAlertRepository memberKeywordAlertRepository;

  @Transactional(readOnly = true)
  public MemberKeywordAlertResponse getKeywordAlerts(UUID memberId) {
    return buildKeywordAlertResponse(memberId);
  }

  /**
   * 키워드 등록: 정규화 > 검증 > 저장 > 목록
   */
  @Transactional
  public MemberKeywordAlertResponse registerKeywordAlert(MemberKeywordAlertRequest request) {
    UUID memberId = request.getMember().getMemberId();
    String displayKeyword = KeywordAlertRule.toDisplayKeyword(request.getKeyword());
    String normalizedKeyword = KeywordAlertRule.normalize(request.getKeyword());

    validateRegistration(memberId, normalizedKeyword);

    MemberKeywordAlert keywordAlert = MemberKeywordAlert.builder()
        .member(request.getMember())
        .keyword(displayKeyword)
        .normalizedKeyword(normalizedKeyword)
        .build();
    // 동시 등록 경합 > unique 제약 위반을 중복 오류로 변환
    try {
      memberKeywordAlertRepository.saveAndFlush(keywordAlert);
    } catch (DataIntegrityViolationException duplicateKeywordException) {
      log.warn("[keyword alert] rejected 400 : duplicate on save, memberId={}", memberId);
      throw new CustomException(ErrorCode.DUPLICATE_KEYWORD_ALERT);
    }
    log.info("[keyword alert] registered : memberId={}, keywordAlertId={}", memberId, keywordAlert.getKeywordAlertId());
    return buildKeywordAlertResponse(memberId);
  }

  /**
   * 키워드 삭제 (본인 것만, 없으면 KEYWORD_ALERT_NOT_FOUND)
   */
  @Transactional
  public MemberKeywordAlertResponse deleteKeywordAlert(MemberKeywordAlertRequest request) {
    UUID memberId = request.getMember().getMemberId();
    MemberKeywordAlert keywordAlert = memberKeywordAlertRepository
        .findByKeywordAlertIdAndMemberMemberId(request.getKeywordAlertId(), memberId)
        .orElseThrow(() -> {
          log.warn("[keyword alert] rejected 404 : not found, memberId={}, keywordAlertId={}",
              memberId, request.getKeywordAlertId());
          return new CustomException(ErrorCode.KEYWORD_ALERT_NOT_FOUND);
        });

    memberKeywordAlertRepository.delete(keywordAlert);
    memberKeywordAlertRepository.flush();
    log.info("[keyword alert] deleted : memberId={}, keywordAlertId={}", memberId, keywordAlert.getKeywordAlertId());
    return buildKeywordAlertResponse(memberId);
  }

  // 등록 검증 (길이 > 중복 > 개수, 실패 시 바로 거부)
  private void validateRegistration(UUID memberId, String normalizedKeyword) {
    if (!KeywordAlertRule.isAllowedLength(normalizedKeyword)) {
      log.warn("[keyword alert] rejected 400 : invalid length {}, memberId={}", normalizedKeyword.length(), memberId);
      throw new CustomException(ErrorCode.INVALID_KEYWORD_ALERT_LENGTH);
    }
    if (memberKeywordAlertRepository.existsByMemberMemberIdAndNormalizedKeyword(memberId, normalizedKeyword)) {
      log.warn("[keyword alert] rejected 400 : duplicate keyword, memberId={}", memberId);
      throw new CustomException(ErrorCode.DUPLICATE_KEYWORD_ALERT);
    }
    if (memberKeywordAlertRepository.countByMemberMemberId(memberId) >= KeywordAlertRule.MAXIMUM_KEYWORD_ALERT_COUNT) {
      log.warn("[keyword alert] rejected 400 : limit exceeded, memberId={}", memberId);
      throw new CustomException(ErrorCode.KEYWORD_ALERT_LIMIT_EXCEEDED);
    }
  }

  private MemberKeywordAlertResponse buildKeywordAlertResponse(UUID memberId) {
    return MemberKeywordAlertResponse.builder()
        .keywordAlerts(memberKeywordAlertRepository.findAllByMemberMemberIdOrderByCreatedDateDesc(memberId))
        .maximumKeywordAlertCount(KeywordAlertRule.MAXIMUM_KEYWORD_ALERT_COUNT)
        .build();
  }
}
