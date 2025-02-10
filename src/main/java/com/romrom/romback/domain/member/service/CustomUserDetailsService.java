package com.romrom.romback.domain.member.service;

import com.romrom.romback.domain.member.domain.Member;
import com.romrom.romback.domain.member.dto.CustomUserDetails;
import com.romrom.romback.domain.member.repository.MemberRepository;
import com.romrom.romback.global.exception.CustomException;
import com.romrom.romback.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;

    @Override
    public CustomUserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.error("회원을 찾을 수 없습니다. 회원 Username: {}", username);
                    return new CustomException(ErrorCode.MEMBER_NOT_FOUND);
                });
        return new CustomUserDetails(member);
    }
}
