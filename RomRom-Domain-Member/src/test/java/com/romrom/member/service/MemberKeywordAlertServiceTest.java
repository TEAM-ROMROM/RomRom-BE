package com.romrom.member.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.member.dto.MemberKeywordAlertRequest;
import com.romrom.member.dto.MemberKeywordAlertResponse;
import com.romrom.member.entity.Member;
import com.romrom.member.entity.MemberKeywordAlert;
import com.romrom.member.repository.MemberKeywordAlertRepository;
import com.romrom.member.util.KeywordAlertRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 키워드 알림 관리 단위 테스트 (#873): 등록 검증(길이·중복·개수 10), 저장 경합 중복, 본인 것만 삭제
 */
@ExtendWith(MockitoExtension.class)
class MemberKeywordAlertServiceTest {

  private static final UUID MEMBER_ID = UUID.randomUUID();

  @Mock
  private MemberKeywordAlertRepository memberKeywordAlertRepository;

  @InjectMocks
  private MemberKeywordAlertService memberKeywordAlertService;

  private static MemberKeywordAlertRequest createRegisterRequest(String keyword) {
    return MemberKeywordAlertRequest.builder()
        .member(Member.builder().memberId(MEMBER_ID).build())
        .keyword(keyword)
        .build();
  }

  private static void assertErrorCode(ErrorCode expectedErrorCode, Runnable action) {
    CustomException customException = Assertions.assertThrows(CustomException.class, action::run);
    Assertions.assertEquals(expectedErrorCode, customException.getErrorCode());
  }

  @Test
  @DisplayName("정상 등록: 표시용·정규화 키워드 저장 > 최신 목록 + 최대 개수 응답")
  void registerKeywordAlert_정상등록() {
    when(memberKeywordAlertRepository.existsByMemberMemberIdAndNormalizedKeyword(MEMBER_ID, "닌텐도 switch"))
        .thenReturn(false);
    when(memberKeywordAlertRepository.countByMemberMemberId(MEMBER_ID)).thenReturn(9L);
    when(memberKeywordAlertRepository.findAllByMemberMemberIdOrderByCreatedDateDesc(MEMBER_ID)).thenReturn(List.of());

    MemberKeywordAlertResponse keywordAlertResponse =
        memberKeywordAlertService.registerKeywordAlert(createRegisterRequest("  닌텐도   Switch "));

    ArgumentCaptor<MemberKeywordAlert> savedKeywordAlertCaptor = ArgumentCaptor.forClass(MemberKeywordAlert.class);
    verify(memberKeywordAlertRepository).saveAndFlush(savedKeywordAlertCaptor.capture());
    Assertions.assertEquals("닌텐도 Switch", savedKeywordAlertCaptor.getValue().getKeyword());
    Assertions.assertEquals("닌텐도 switch", savedKeywordAlertCaptor.getValue().getNormalizedKeyword());
    Assertions.assertEquals(KeywordAlertRule.MAXIMUM_KEYWORD_ALERT_COUNT, keywordAlertResponse.getMaximumKeywordAlertCount());
  }

  @Test
  @DisplayName("정규화 후 1자·21자·공백만 > INVALID_KEYWORD_ALERT_LENGTH, 저장 안 함")
  void registerKeywordAlert_길이위반() {
    assertErrorCode(ErrorCode.INVALID_KEYWORD_ALERT_LENGTH,
        () -> memberKeywordAlertService.registerKeywordAlert(createRegisterRequest(" 가 ")));
    assertErrorCode(ErrorCode.INVALID_KEYWORD_ALERT_LENGTH,
        () -> memberKeywordAlertService.registerKeywordAlert(createRegisterRequest("가".repeat(21))));
    assertErrorCode(ErrorCode.INVALID_KEYWORD_ALERT_LENGTH,
        () -> memberKeywordAlertService.registerKeywordAlert(createRegisterRequest("   ")));

    verify(memberKeywordAlertRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("같은 정규화 키워드 > DUPLICATE_KEYWORD_ALERT")
  void registerKeywordAlert_중복() {
    when(memberKeywordAlertRepository.existsByMemberMemberIdAndNormalizedKeyword(MEMBER_ID, "switch"))
        .thenReturn(true);

    assertErrorCode(ErrorCode.DUPLICATE_KEYWORD_ALERT,
        () -> memberKeywordAlertService.registerKeywordAlert(createRegisterRequest(" SWITCH ")));
    verify(memberKeywordAlertRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("이미 10개 등록 > KEYWORD_ALERT_LIMIT_EXCEEDED")
  void registerKeywordAlert_개수초과() {
    when(memberKeywordAlertRepository.existsByMemberMemberIdAndNormalizedKeyword(MEMBER_ID, "아이패드"))
        .thenReturn(false);
    when(memberKeywordAlertRepository.countByMemberMemberId(MEMBER_ID))
        .thenReturn((long) KeywordAlertRule.MAXIMUM_KEYWORD_ALERT_COUNT);

    assertErrorCode(ErrorCode.KEYWORD_ALERT_LIMIT_EXCEEDED,
        () -> memberKeywordAlertService.registerKeywordAlert(createRegisterRequest("아이패드")));
    verify(memberKeywordAlertRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("동시 등록으로 unique 제약 위반 > DUPLICATE_KEYWORD_ALERT")
  void registerKeywordAlert_저장경합중복() {
    when(memberKeywordAlertRepository.existsByMemberMemberIdAndNormalizedKeyword(MEMBER_ID, "아이패드"))
        .thenReturn(false);
    when(memberKeywordAlertRepository.countByMemberMemberId(MEMBER_ID)).thenReturn(0L);
    when(memberKeywordAlertRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique"));

    assertErrorCode(ErrorCode.DUPLICATE_KEYWORD_ALERT,
        () -> memberKeywordAlertService.registerKeywordAlert(createRegisterRequest("아이패드")));
  }

  @Test
  @DisplayName("본인 키워드 삭제 > 삭제 후 목록")
  void deleteKeywordAlert_본인삭제() {
    UUID keywordAlertId = UUID.randomUUID();
    MemberKeywordAlert keywordAlert = MemberKeywordAlert.builder().keywordAlertId(keywordAlertId).keyword("아이패드").build();
    when(memberKeywordAlertRepository.findByKeywordAlertIdAndMemberMemberId(keywordAlertId, MEMBER_ID))
        .thenReturn(Optional.of(keywordAlert));
    when(memberKeywordAlertRepository.findAllByMemberMemberIdOrderByCreatedDateDesc(MEMBER_ID)).thenReturn(List.of());

    MemberKeywordAlertResponse keywordAlertResponse = memberKeywordAlertService.deleteKeywordAlert(
        MemberKeywordAlertRequest.builder()
            .member(Member.builder().memberId(MEMBER_ID).build())
            .keywordAlertId(keywordAlertId)
            .build());

    verify(memberKeywordAlertRepository).delete(keywordAlert);
    Assertions.assertTrue(keywordAlertResponse.getKeywordAlerts().isEmpty());
  }

  @Test
  @DisplayName("없거나 다른 회원 키워드 > KEYWORD_ALERT_NOT_FOUND, 삭제 안 함")
  void deleteKeywordAlert_없는키워드() {
    UUID otherMemberKeywordAlertId = UUID.randomUUID();
    when(memberKeywordAlertRepository.findByKeywordAlertIdAndMemberMemberId(otherMemberKeywordAlertId, MEMBER_ID))
        .thenReturn(Optional.empty());

    assertErrorCode(ErrorCode.KEYWORD_ALERT_NOT_FOUND, () -> memberKeywordAlertService.deleteKeywordAlert(
        MemberKeywordAlertRequest.builder()
            .member(Member.builder().memberId(MEMBER_ID).build())
            .keywordAlertId(otherMemberKeywordAlertId)
            .build()));
    verify(memberKeywordAlertRepository, never()).delete(any());
  }
}
