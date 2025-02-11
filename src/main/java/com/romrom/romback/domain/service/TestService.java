package com.romrom.romback.domain.service;

import com.romrom.romback.domain.object.constant.AccountStatus;
import com.romrom.romback.domain.object.constant.Role;
import com.romrom.romback.domain.object.dto.TestRequest;
import com.romrom.romback.domain.object.dto.TestResponse;
import com.romrom.romback.domain.object.postgres.Member;
import com.romrom.romback.domain.repository.postgres.MemberRepository;
import com.romrom.romback.global.exception.CustomException;
import com.romrom.romback.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class TestService {
  private final MemberRepository memberRepository;

  //TODO: 테스트 회원가입 및 로그인 구현 필요
  public TestResponse signUp(TestRequest request) {
    String email = request.getEmail();

    // 이메일 검증
    if (memberRepository.existsByEmail(email)) {
      log.error("이미 가입된 이메일입니다.: {}", email);
      throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    // 신규 가짜 회원 저장
    Member member = Member.builder()
        .email(email)
        .socialId("TEST")
        .socialPlatform(request.getSocialPlatform())
        .profileUrl("TEST")
        .role(Role.ROLE_USER)
        .accountStatus(AccountStatus.ACTIVE_ACCOUNT)
        .build();

    memberRepository.save(member);

    log.debug("가짜 회원가입 성공: email={}", email);

    // TODO: 실제 토큰 발급 로직 적용 (여기서는 더미 토큰 사용)
    return TestResponse.builder()
        .accessToken("dummyAccessTokenForSignup")
        .refreshToken("dummyRefreshTokenForSignup")
        .build();
  }

}
