package com.romrom.application.service;

import com.romrom.application.dto.AdminResponse;
import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.auth.jwt.JwtUtil;
import com.romrom.common.constant.Role;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.member.entity.Member;
import com.romrom.member.repository.MemberRepository;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminAuthService {

    private static final String REFRESH_KEY_PREFIX = "RT:ADMIN:";

    private final BCryptPasswordEncoder passwordEncoder;
    private final MemberRepository memberRepository;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 관리자 JWT 인증
     * 순서: 이메일 조회 → 비밀번호 검증 → 관리자 권한 검증 → 토큰 발급
     */
    @Transactional(readOnly = true)
    public AdminResponse authenticateWithJwt(String username, String password) {
        log.info("관리자 로그인 시도: {}", username);

        Member adminMember = memberRepository.findByEmail(username)
            .orElseThrow(() -> {
                log.warn("존재하지 않는 계정으로 로그인 시도: {}", username);
                return new CustomException(ErrorCode.INVALID_CREDENTIALS);
            });

        // 비밀번호 검증 (역할 확인 전에 수행 - 정보 노출 최소화)
        if (!passwordEncoder.matches(password, adminMember.getPassword())) {
            log.warn("비밀번호 불일치: {}", username);
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 관리자 권한 검증
        if (adminMember.getRole() != Role.ROLE_ADMIN) {
            log.warn("관리자 권한이 없는 계정 접근 시도: {}", username);
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        CustomUserDetails customUserDetails = new CustomUserDetails(adminMember);
        String accessToken = jwtUtil.createAccessToken(customUserDetails);
        String refreshToken = jwtUtil.createRefreshToken(customUserDetails);

        redisTemplate.opsForValue().set(
            REFRESH_KEY_PREFIX + adminMember.getMemberId(),
            refreshToken,
            jwtUtil.getRefreshExpirationTime(),
            TimeUnit.MILLISECONDS
        );

        log.info("관리자 로그인 성공: {}", username);

        return AdminResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .username(username)
            .role(adminMember.getRole().name())
            .build();
    }

    /**
     * 관리자 로그아웃 (토큰 무효화)
     */
    @Transactional(readOnly = true)
    public void logout(String refreshToken) {
        log.info("관리자 로그아웃 시도");

        String username = jwtUtil.getUsername(refreshToken);
        Member adminMember = memberRepository.findByEmail(username)
            .orElseThrow(() -> {
                log.error("로그아웃 실패 - 회원을 찾을 수 없음: {}", username);
                return new CustomException(ErrorCode.MEMBER_NOT_FOUND);
            });

        String refreshTokenKey = REFRESH_KEY_PREFIX + adminMember.getMemberId();
        jwtUtil.deactivateToken(refreshToken, refreshTokenKey);

        log.info("관리자 로그아웃 완료: {}", username);
    }

    /**
     * 관리자 토큰 재발급
     */
    @Transactional(readOnly = true)
    public AdminResponse refreshToken(String refreshToken) {
        log.debug("관리자 토큰 재발급 요청");

        if (!jwtUtil.validateToken(refreshToken)) {
            log.warn("유효하지 않은 리프레시 토큰");
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        if (!"refresh".equals(jwtUtil.getCategory(refreshToken))) {
            log.warn("리프레시 토큰이 아닌 토큰으로 재발급 시도");
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        CustomUserDetails customUserDetails = (CustomUserDetails) jwtUtil
            .getAuthentication(refreshToken).getPrincipal();
        String key = REFRESH_KEY_PREFIX + customUserDetails.getMember().getMemberId();
        Object stored = redisTemplate.opsForValue().get(key);

        if (stored == null || !refreshToken.equals(stored.toString())) {
            log.warn("저장된 리프레시 토큰과 불일치: memberId={}", customUserDetails.getMember().getMemberId());
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        if (customUserDetails.getMember().getRole() != Role.ROLE_ADMIN) {
            log.warn("관리자 권한이 없는 토큰으로 재발급 시도");
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        String newAccessToken = jwtUtil.createAccessToken(customUserDetails);
        log.info("관리자 토큰 재발급 완료: username={}", customUserDetails.getUsername());

        return AdminResponse.builder()
            .accessToken(newAccessToken)
            .refreshToken(refreshToken)
            .username(customUserDetails.getUsername())
            .role(customUserDetails.getMember().getRole().name())
            .build();
    }
}
