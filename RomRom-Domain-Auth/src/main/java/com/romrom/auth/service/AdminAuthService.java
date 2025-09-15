package com.romrom.auth.service;

import com.romrom.auth.dto.AdminResponse;
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
     */
    @Transactional
    public AdminResponse authenticateWithJwt(String username, String password) {
        log.debug("관리자 JWT 로그인 시도: {}", username);
        
        // 관리자 계정 조회
        Member adminMember = memberRepository.findByEmail(username)
            .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));
        
        // 관리자 권한 확인
        if (adminMember.getRole() != Role.ROLE_ADMIN) {
            log.error("관리자 권한이 없는 계정: {}", username);
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        
        // 비밀번호 검증
        if (!passwordEncoder.matches(password, adminMember.getPassword())) {
            log.error("비밀번호 불일치: {}", username);
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }
        
        // JWT 토큰 생성
        CustomUserDetails customUserDetails = new CustomUserDetails(adminMember);
        String accessToken = jwtUtil.createAccessToken(customUserDetails);
        String refreshToken = jwtUtil.createRefreshToken(customUserDetails);
        
        // RefreshToken Redis 저장 (키: "RT:ADMIN:{memberId}")
        redisTemplate.opsForValue().set(
            REFRESH_KEY_PREFIX + adminMember.getMemberId(),
            refreshToken,
            jwtUtil.getRefreshExpirationTime(),
            TimeUnit.MILLISECONDS
        );
        
        log.info("관리자 JWT 로그인 성공: {}", username);
        
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
    @Transactional
    public void logout(String accessToken) {
        try {
            // 토큰에서 사용자 정보 추출
            String username = jwtUtil.getUsername(accessToken);
            Member adminMember = memberRepository.findByEmail(username)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
            
            // Redis에서 리프레시 토큰 키
            String refreshTokenKey = REFRESH_KEY_PREFIX + adminMember.getMemberId();
            
            // 토큰 비활성화 (블랙리스트 등록 + 리프레시 토큰 삭제)
            jwtUtil.deactivateToken(accessToken, refreshTokenKey);
            
            log.info("관리자 로그아웃 완료: {}", username);
        } catch (Exception e) {
            log.error("관리자 로그아웃 실패: {}", e.getMessage());
        }
    }
    
    /**
     * 관리자 토큰 재발급
     */
    @Transactional
    public AdminResponse refreshToken(String refreshToken) {
        log.debug("관리자 토큰 재발급 요청");
        
        // 리프레시 토큰 검증
        if (!jwtUtil.validateToken(refreshToken)) {
            log.error("유효하지 않은 리프레시 토큰");
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        
        // 토큰 카테고리 확인
        if (!"refresh".equals(jwtUtil.getCategory(refreshToken))) {
            log.error("리프레시 토큰이 아닙니다");
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        
        // Redis 최신 RT 유효성 확인
        CustomUserDetails customUserDetails = (CustomUserDetails) jwtUtil
            .getAuthentication(refreshToken).getPrincipal();
        String key = REFRESH_KEY_PREFIX + customUserDetails.getMember().getMemberId();
        Object stored = redisTemplate.opsForValue().get(key);
        if (stored == null || !refreshToken.equals(stored.toString())) {
            log.error("저장된 리프레시 토큰과 불일치");
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        
        // 관리자 권한 재확인
        if (customUserDetails.getMember().getRole() != Role.ROLE_ADMIN) {
            log.error("관리자 권한이 없는 토큰");
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        
        String newAccessToken = jwtUtil.createAccessToken(customUserDetails);
        
        return AdminResponse.builder()
            .accessToken(newAccessToken)
            .refreshToken(refreshToken)
            .username(customUserDetails.getUsername())
            .role(customUserDetails.getMember().getRole().name())
            .build();
    }
}