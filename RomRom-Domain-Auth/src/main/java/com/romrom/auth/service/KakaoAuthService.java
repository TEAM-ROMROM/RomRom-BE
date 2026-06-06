package com.romrom.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.romrom.auth.dto.KakaoFirebaseTokenRequest;
import com.romrom.auth.dto.KakaoFirebaseTokenResponse;
import com.romrom.common.constant.SocialPlatform;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.EmailAlreadyRegisteredException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.member.entity.Member;
import com.romrom.member.repository.MemberRepository;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class KakaoAuthService {

  private static final String KAKAO_USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";
  private static final String KAKAO_UID_PREFIX = "kakao:";

  private final OkHttpClient okHttpClient;
  private final ObjectMapper objectMapper;
  private final MemberRepository memberRepository;

  /**
   * 카카오 accessToken으로 Firebase Custom Token 발급
   * 1. 카카오 사용자 정보 API 호출 → 카카오 회원번호 획득
   * 2. 기존 회원 pre-matching: firebaseUid 1순위, email 2순위 (oidc.kakao 마이그레이션 대응)
   * 3. firebaseUid = "kakao:{카카오회원번호}"로 Firebase Custom Token 생성
   */
  @Transactional
  public KakaoFirebaseTokenResponse issueFirebaseCustomToken(KakaoFirebaseTokenRequest request) {
    String kakaoAccessToken = request.getAccessToken();

    if (kakaoAccessToken == null || kakaoAccessToken.isBlank()) {
      throw new CustomException(ErrorCode.EMPTY_SOCIAL_AUTH_TOKEN);
    }

    long kakaoUserId = fetchKakaoUserId(kakaoAccessToken);
    String kakaoFirebaseUid = KAKAO_UID_PREFIX + kakaoUserId;

    // 기존 회원 pre-matching: /login의 2차 findByFirebaseUid 조회가 성공하도록 firebaseUid 선세팅
    // Custom Token 로그인 시 Firebase ID Token에 email이 없으므로 /login의 1차 email 조회가 항상 스킵됨
    preMatchExistingMember(kakaoFirebaseUid, request.getEmail());

    String firebaseCustomToken = createFirebaseCustomToken(kakaoFirebaseUid);

    log.debug("Firebase Custom Token 발급 완료: kakaoUserId={}, firebaseUid={}", kakaoUserId, kakaoFirebaseUid);

    return KakaoFirebaseTokenResponse.builder()
        .customToken(firebaseCustomToken)
        .build();
  }

  /**
   * 기존 카카오 회원의 firebaseUid를 미리 세팅하여 /login 2차 조회가 성공하도록 준비
   * 1순위: firebaseUid로 조회 (이미 Custom Token 방식 사용 중인 회원)
   * 2순위: email로 조회 (기존 oidc.kakao 회원 → custom token 방식 전환)
   */
  private void preMatchExistingMember(String kakaoFirebaseUid, String requestEmail) {
    // 1순위: 이미 firebaseUid가 세팅된 회원 (재로그인)
    Optional<Member> existMember = memberRepository.findByFirebaseUid(kakaoFirebaseUid);

    // 2순위: email로 기존 카카오 회원 조회 (oidc.kakao 마이그레이션 회원)
    if (existMember.isEmpty() && requestEmail != null && !requestEmail.isBlank()) {
      existMember = memberRepository.findByEmail(requestEmail);
    }

    if (existMember.isEmpty()) {
      return;
    }

    Member member = existMember.get();

    if (member.getSocialPlatform() != SocialPlatform.KAKAO) {
      throw new EmailAlreadyRegisteredException(member.getSocialPlatform());
    }

    // oidc.kakao → custom token 전환 시 firebaseUid 세팅 (기존 oidc.kakao uid와 다른 경우 포함)
    if (!kakaoFirebaseUid.equals(member.getFirebaseUid())) {
      member.setFirebaseUid(kakaoFirebaseUid);
      memberRepository.save(member);
      log.debug("기존 카카오 회원 firebaseUid 세팅 완료: email={}, firebaseUid={}", requestEmail, kakaoFirebaseUid);
    }
  }

  /**
   * 카카오 사용자 정보 API를 호출하여 카카오 회원번호를 반환
   */
  private long fetchKakaoUserId(String kakaoAccessToken) {
    Request kakaoRequest = new Request.Builder()
        .url(KAKAO_USER_INFO_URL)
        .addHeader("Authorization", "Bearer " + kakaoAccessToken)
        .get()
        .build();

    try (Response kakaoResponse = okHttpClient.newCall(kakaoRequest).execute()) {
      if (!kakaoResponse.isSuccessful() || kakaoResponse.body() == null) {
        log.error("카카오 사용자 정보 조회 실패: status={}", kakaoResponse.code());
        throw new CustomException(ErrorCode.KAKAO_API_ERROR);
      }

      String responseBodyJson = kakaoResponse.body().string();
      JsonNode kakaoUserInfo = objectMapper.readTree(responseBodyJson);
      JsonNode kakaoUserIdNode = kakaoUserInfo.get("id");

      if (kakaoUserIdNode == null || kakaoUserIdNode.isNull()) {
        log.error("카카오 사용자 정보에서 id 필드 없음: body={}", responseBodyJson);
        throw new CustomException(ErrorCode.INVALID_SOCIAL_MEMBER_INFO);
      }

      return kakaoUserIdNode.asLong();

    } catch (IOException ioException) {
      log.error("카카오 API 호출 중 IO 오류: {}", ioException.getMessage());
      throw new CustomException(ErrorCode.KAKAO_API_ERROR);
    }
  }

  /**
   * Firebase Admin SDK로 Custom Token 생성
   */
  private String createFirebaseCustomToken(String kakaoFirebaseUid) {
    try {
      return FirebaseAuth.getInstance().createCustomToken(kakaoFirebaseUid);
    } catch (FirebaseAuthException firebaseAuthException) {
      log.error("Firebase Custom Token 발급 실패: uid={}, code={}, message={}",
          kakaoFirebaseUid, firebaseAuthException.getAuthErrorCode(), firebaseAuthException.getMessage());
      throw new CustomException(ErrorCode.FIREBASE_CUSTOM_TOKEN_ISSUE_FAILED);
    }
  }
}
