//package com.romrom.romback.domain.service;
//
//import com.romrom.romback.domain.object.constant.SocialPlatform;
//import com.romrom.romback.domain.object.dto.AuthRequest;
//import com.romrom.romback.domain.object.dto.OAuthMemberInfo;
//import com.romrom.romback.global.config.OAuthConfig;
//import com.romrom.romback.global.exception.CustomException;
//import com.romrom.romback.global.exception.ErrorCode;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.http.*;
//import org.springframework.stereotype.Service;
//import org.springframework.web.client.RestTemplate;
//
//import java.util.Map;
//
//@Service
//@Slf4j
//@RequiredArgsConstructor
//public class OAuthService {
//
//  private final OAuthConfig oAuthConfig;
//
//  public OAuthMemberInfo getMemberInfoFromOAuthPlatform(AuthRequest request) {
//    String socialAuthToken = request.getSocialAuthToken();
//    SocialPlatform socialPlatform = request.getSocialPlatform();
//
//    // AUTH 토큰 유효 확인
//    if (socialAuthToken == null || socialAuthToken.trim().isEmpty()) {
//      throw new CustomException(ErrorCode.EMPTY_SOCIAL_AUTH_TOKEN);
//    }
//
//    // 회원 정보 받는 플랫폼 BASE URL 불러오기
//    String userInfoUrl;
//    if (socialPlatform == SocialPlatform.GOOGLE) {
//      userInfoUrl = oAuthConfig.getGoogleUserInfoUrl();
//    } else if (socialPlatform == SocialPlatform.KAKAO) {
//      userInfoUrl = oAuthConfig.getKakaoUserInfoUrl();
//    } else {
//      throw new CustomException(ErrorCode.INVALID_SOCIAL_PLATFORM);
//    }
//
//    // HTTP 요청 -> 소셜 API 호출 -> ResponseBody 반환
//    Map<String, Object> userAttributes = getSocialUserInfoMapFromApi(userInfoUrl, socialAuthToken);
//
//    OAuthMemberInfo oAuthMemberInfo;
//    if (socialPlatform.equals(SocialPlatform.GOOGLE)) {
//      // 구글 회원 정보
//      oAuthMemberInfo = OAuthMemberInfo.extractGoogleUserInfo(userAttributes);
//    } else if (socialPlatform.equals(SocialPlatform.KAKAO)) {
//      // 카카오 회원 정보
//      oAuthMemberInfo = OAuthMemberInfo.extractKakaoUserInfo(userAttributes);
//    } else {
//      throw new CustomException(ErrorCode.INVALID_SOCIAL_PLATFORM);
//    }
//
//    // email, socialId 유효 확인
//    if (oAuthMemberInfo.getEmail() == null || oAuthMemberInfo.getSocialId() == null) {
//      log.error("소셜 로그인 사용자 정보 파싱 실패: {}", userAttributes);
//      throw new CustomException(ErrorCode.INVALID_SOCIAL_MEMBER_INFO);
//    }
//
//    return oAuthMemberInfo;
//  }
//
//  /**
//   * 소셜 API 호출하여 응답을 Map 반환
//   */
//  private Map<String, Object> getSocialUserInfoMapFromApi(String userInfoUrl, String socialAuthToken) {
//    RestTemplate restTemplate = new RestTemplate();
//    HttpHeaders headers = new HttpHeaders();
//    headers.set("Authorization", "Bearer " + socialAuthToken);
//    HttpEntity<?> entity = new HttpEntity<>(headers);
//
//    ResponseEntity<Map> response = restTemplate.exchange(userInfoUrl, HttpMethod.GET, entity, Map.class);
//
//    if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
//      log.error("소셜 API 호출 실패: status={}, body={}", response.getStatusCode(), response.getBody());
//      throw new CustomException(ErrorCode.SOCIAL_API_ERROR);
//    }
//
//    return response.getBody();
//  }
//
//
//}
