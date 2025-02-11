package com.romrom.romback.domain.object.dto;

import com.romrom.romback.domain.object.constant.SocialPlatform;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@ToString
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class OAuthMemberInfo {
  private String socialId;
  private SocialPlatform socialPlatform;
  private String email;
  private String profileUrl;

  /**
   * Google API 응답 파싱 메서드
   * 응답 예시: { "sub": "...", "email": "...", "picture": "..." }
   */
  public static OAuthMemberInfo extractGoogleUserInfo(Map<String, Object> userAttributes) {
    String socialId = (String) userAttributes.get("sub");
    String email = (String) userAttributes.get("email");
    String profileUrl = (String) userAttributes.get("picture");

    return OAuthMemberInfo.builder()
        .socialId(socialId)
        .socialPlatform(SocialPlatform.GOOGLE)
        .email(email)
        .profileUrl(profileUrl)
        .build();
  }

  /**
   * Kakao API 응답 파싱 메서드
   * 응답 예시: { "id": ..., "kakao_account": { "email": "...", "profile": { "profile_image_url": "..." } } }
   */
  public static OAuthMemberInfo extractKakaoUserInfo(Map<String, Object> userAttributes) {
    String socialId = String.valueOf(userAttributes.get("id"));
    Map<String, Object> kakaoAccount = (Map<String, Object>) userAttributes.get("kakao_account");
    String email = null;
    String profileUrl = null;
    if (kakaoAccount != null) {
      email = (String) kakaoAccount.get("email");
      Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
      if (profile != null) {
        profileUrl = (String) profile.get("profile_image_url");
      }
    }

    return OAuthMemberInfo.builder()
        .socialId(socialId)
        .socialPlatform(SocialPlatform.KAKAO)
        .email(email)
        .profileUrl(profileUrl)
        .build();
  }
}
