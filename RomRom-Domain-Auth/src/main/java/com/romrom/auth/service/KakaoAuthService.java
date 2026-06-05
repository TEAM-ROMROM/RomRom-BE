package com.romrom.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.romrom.auth.dto.KakaoFirebaseTokenRequest;
import com.romrom.auth.dto.KakaoFirebaseTokenResponse;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class KakaoAuthService {

  private static final String KAKAO_USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";
  private static final String KAKAO_UID_PREFIX = "kakao:";

  private final OkHttpClient okHttpClient;
  private final ObjectMapper objectMapper;

  /**
   * 카카오 accessToken으로 Firebase Custom Token 발급
   * 1. 카카오 사용자 정보 API 호출 → 카카오 회원번호 획득
   * 2. firebaseUid = "kakao:{카카오회원번호}"로 Firebase Custom Token 생성
   */
  public KakaoFirebaseTokenResponse issueFirebaseCustomToken(KakaoFirebaseTokenRequest request) {
    String kakaoAccessToken = request.getAccessToken();

    if (kakaoAccessToken == null || kakaoAccessToken.isBlank()) {
      throw new CustomException(ErrorCode.EMPTY_SOCIAL_AUTH_TOKEN);
    }

    long kakaoUserId = fetchKakaoUserId(kakaoAccessToken);
    String kakaoFirebaseUid = KAKAO_UID_PREFIX + kakaoUserId;

    String firebaseCustomToken = createFirebaseCustomToken(kakaoFirebaseUid);

    log.debug("Firebase Custom Token 발급 완료: kakaoUserId={}, firebaseUid={}", kakaoUserId, kakaoFirebaseUid);

    return KakaoFirebaseTokenResponse.builder()
        .customToken(firebaseCustomToken)
        .build();
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
