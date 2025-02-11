package com.romrom.romback.domain.service;

import com.romrom.romback.domain.object.dto.AuthRequest;
import com.romrom.romback.domain.repository.postgres.MemberRepository;
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
   */
  @Transactional
  public Void signUp(AuthRequest request) {
    return null;
  }

}
