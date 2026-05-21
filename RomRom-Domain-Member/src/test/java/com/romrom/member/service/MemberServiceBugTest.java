package com.romrom.member.service;

import static me.suhsaechan.suhlogger.util.SuhLogger.lineLog;
import static me.suhsaechan.suhlogger.util.SuhLogger.superLog;
import static me.suhsaechan.suhlogger.util.SuhLogger.timeLog;

import com.romrom.common.constant.AccountStatus;
import com.romrom.member.dto.MemberRequest;
import com.romrom.member.dto.MemberResponse;
import com.romrom.member.entity.Member;
import com.romrom.member.repository.MemberRepository;
import com.romrom.web.RomBackApplication;
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
class MemberServiceBugTest {

  @Autowired
  MemberService memberService;

  @Autowired
  MemberRepository memberRepository;

  @Test
  public void mainTest() {
    lineLog("테스트시작");

    lineLog(null);
    timeLog(this::saveTermsAgreement_마케팅동의false여도_알림동의는_true설정_테스트);
    lineLog(null);
    timeLog(this::saveTermsAgreement_마케팅동의true여도_알림동의는_true설정_테스트);
    lineLog(null);
    timeLog(this::deleteMemberRelatedData_accountStatus_DELETE_ACCOUNT설정_테스트);
    lineLog(null);

    lineLog("테스트종료");
  }

  // Bug 6: 마케팅 동의 false여도 활동/채팅/컨텐츠/거래 알림 동의는 true 고정
  @Transactional
  public void saveTermsAgreement_마케팅동의false여도_알림동의는_true설정_테스트() {
    Member member = memberRepository.findAll().stream()
        .filter(m -> !m.getIsDeleted())
        .findFirst()
        .orElseThrow(() -> new RuntimeException("활성 회원 없음"));

    MemberRequest request = MemberRequest.builder()
        .member(member)
        .isMarketingInfoAgreed(false)
        .build();

    MemberResponse response = memberService.saveTermsAgreement(request);
    Member saved = response.getMember();

    superLog(saved);
    Assertions.assertFalse(saved.getIsMarketingInfoAgreed(), "마케팅 동의는 false 이어야 함");
    Assertions.assertTrue(saved.getIsActivityNotificationAgreed(), "활동 알림 동의는 true 고정이어야 함 (Bug 6)");
    Assertions.assertTrue(saved.getIsChatNotificationAgreed(), "채팅 알림 동의는 true 고정이어야 함 (Bug 6)");
    Assertions.assertTrue(saved.getIsContentNotificationAgreed(), "컨텐츠 알림 동의는 true 고정이어야 함 (Bug 6)");
    Assertions.assertTrue(saved.getIsTradeNotificationAgreed(), "거래 알림 동의는 true 고정이어야 함 (Bug 6)");
    lineLog("saveTermsAgreement 마케팅false 알림true 검증 완료");
  }

  // Bug 6 보완: 마케팅 동의 true도 동일하게 알림 동의 true
  @Transactional
  public void saveTermsAgreement_마케팅동의true여도_알림동의는_true설정_테스트() {
    Member member = memberRepository.findAll().stream()
        .filter(m -> !m.getIsDeleted())
        .findFirst()
        .orElseThrow(() -> new RuntimeException("활성 회원 없음"));

    MemberRequest request = MemberRequest.builder()
        .member(member)
        .isMarketingInfoAgreed(true)
        .build();

    MemberResponse response = memberService.saveTermsAgreement(request);
    Member saved = response.getMember();

    superLog(saved);
    Assertions.assertTrue(saved.getIsMarketingInfoAgreed(), "마케팅 동의는 true 이어야 함");
    Assertions.assertTrue(saved.getIsActivityNotificationAgreed(), "활동 알림 동의는 true 고정이어야 함");
    Assertions.assertTrue(saved.getIsChatNotificationAgreed(), "채팅 알림 동의는 true 고정이어야 함");
    Assertions.assertTrue(saved.getIsContentNotificationAgreed(), "컨텐츠 알림 동의는 true 고정이어야 함");
    Assertions.assertTrue(saved.getIsTradeNotificationAgreed(), "거래 알림 동의는 true 고정이어야 함");
    lineLog("saveTermsAgreement 마케팅true 알림true 검증 완료");
  }

  // Bug 5: deleteMemberRelatedData → accountStatus DELETE_ACCOUNT 설정 검증
  @Transactional
  public void deleteMemberRelatedData_accountStatus_DELETE_ACCOUNT설정_테스트() {
    Member member = memberRepository.findAll().stream()
        .filter(m -> !m.getIsDeleted()
            && m.getAccountStatus() == AccountStatus.ACTIVE_ACCOUNT)
        .findFirst()
        .orElse(null);

    if (member == null) {
      lineLog("ACTIVE_ACCOUNT 회원 없어 테스트 스킵");
      return;
    }

    MemberRequest request = MemberRequest.builder()
        .member(member)
        .build();

    memberService.deleteMemberRelatedData(request);

    superLog(member.getAccountStatus());
    Assertions.assertEquals(AccountStatus.DELETE_ACCOUNT, member.getAccountStatus(),
        "deleteMemberRelatedData 후 accountStatus가 DELETE_ACCOUNT 이어야 함 (Bug 5)");
    lineLog("deleteMemberRelatedData accountStatus DELETE_ACCOUNT 검증 완료");
  }
}
