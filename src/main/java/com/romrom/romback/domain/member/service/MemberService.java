package com.romrom.romback.domain.member.service;

import com.romrom.romback.domain.member.domain.AccountStatus;
import com.romrom.romback.domain.member.domain.Member;
import com.romrom.romback.domain.member.domain.Role;
import com.romrom.romback.domain.authentication.dto.SignUpRequest;
import com.romrom.romback.domain.member.repository.MemberRepository;
import com.romrom.romback.global.exception.CustomException;
import com.romrom.romback.global.exception.ErrorCode;
import com.romrom.romback.global.util.BaseResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberService {

  private final MemberRepository memberRepository;
  private final BCryptPasswordEncoder bCryptPasswordEncoder;

  /**
   * 회원가입
   *
   * @param request username, password, nickname
   * @return 없음
   */
  @Transactional
  public BaseResponse<Void> signUp(SignUpRequest request) {

    // 사용자 아이디 검증 (중복 아이디 사용 불가)
    if (memberRepository.existsByUsername(request.getUsername())) {
      log.error("이미 가입된 아이디입니다.: {}", request.getUsername());
      throw new CustomException(ErrorCode.DUPLICATE_USERNAME);
    }

    memberRepository.save(Member.builder()
        .username(request.getUsername())
        .password(bCryptPasswordEncoder.encode(request.getPassword()))
        .nickname(request.getNickname())
        .role(Role.ROLE_USER)
        .accountStatus(AccountStatus.ACTIVE_ACCOUNT)
        .build()
    );
    log.debug("회원가입 성공: username={}", request.getUsername());

    return BaseResponse.success(null);
  }


}
