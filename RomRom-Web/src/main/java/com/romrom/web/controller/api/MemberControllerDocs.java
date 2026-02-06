package com.romrom.web.controller.api;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.common.dto.Author;
import com.romrom.member.dto.MemberRequest;
import com.romrom.member.dto.MemberResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;

public interface MemberControllerDocs {

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.01.31", author = Author.WISEUNGJAE, issueNumber = 459, description = "회원 접속 상태 갱신"),
  })
  @Operation(
      summary = "회원 접속 상태 갱신",
      description = """
            ## 인증(JWT): **필요**
          
            ## 요청 파라미터
            - 없음
            - jwt 토큰을 통해 회원 식별
          
            ## 반환값
            - HTTP 상태 코드 200 (OK): 요청이 성공적으로 처리됨
          
            ## 설명
            - 마지막 접속 시간을 업데이트 합니다.
            - 60초에 한번 업데이트가 가능합니다.
          """
  )
  ResponseEntity<Void> updateHeartbeat(CustomUserDetails customUserDetails);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2025.03.04", author = Author.SUHSAECHAN, issueNumber = 32, description = "파라미터 수정: memberProductCategories -> preferredCategories"),
      @ApiChangeLog(date = "2025.02.23", author = Author.SUHSAECHAN, issueNumber = 32, description = "회원 선호 카테고리 저장 API 추가"),
  })
  @Operation(
      summary = "회원 선호 카테고리 저장",
      description = """
            ## 인증(JWT): **필요**
          
            ## 요청 파라미터 (MemberRequest)
            - **`preferredCategories`**: 회원이 선호하는 상품 카테고리 리스트 (List<Integer>)
          
            ## 반환값
            - HTTP 상태 코드 201 (CREATED): 요청이 성공적으로 처리됨
          
            ## 에러코드
            - **`INVALID_MEMBER`**: 유효하지 않은 회원 정보입니다.
            - **`INVALID_CATEGORY_CODE`**: 존재하지 않는 카테고리 코드입니다.
          """
  )
  ResponseEntity<Void> saveMemberItemCategories(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute MemberRequest request);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2025.07.14", author = Author.BAEKJIHOON, issueNumber = 214, description = "사용자 위치정보 중복 저장 방지 (중복 저장 요청시 기존 정보 업데이트)"),
      @ApiChangeLog(date = "2025.04.01", author = Author.SUHSAECHAN, issueNumber = 101, description = "요청 파라미터 삭제: fullAddress, roadAddress, ri에 대한 null값 허용"),
      @ApiChangeLog(date = "2025.03.11", author = Author.BAEKJIHOON, issueNumber = 50, description = "사용자 위치인증 데이터 저장"),
  })
  @Operation(
      summary = "사용자 위치 데이터 저장",
      description = """
            ## 인증(JWT): **필요**
          
            ## 요청 파라미터 (MemberRequest)
            - **`longitude`**: 경도 (double)
            - **`latitude`**: 위도 (double)
            - **`siDo`**: 시/도 (String)
            - **`siGunGu`**: 시/군/구 (String)
            - **`eupMyoenDong`**: 읍/면/동 (String)
            - **`ri`**: 리 (String: null 허용)
          
            ## 반환값
            성공시 : 201 CREATED
          
            ## 에러코드
            - **`INVALID_REQUEST`**: 잘못된 입력값이 요청되었습니다.
            
            ## 유의사항
            - 기존에 위치정보를 저장한 사용자가 해당 API를 통해 재 요청시, 저장된 정보를 새롭게 업데이트합니다
          """
  )
  ResponseEntity<Void> saveMemberLocation(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute MemberRequest request);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2025.11.28", author = Author.BAEKJIHOON, issueNumber = 411, description = "totalLikeCount 반환값 추가"),
      @ApiChangeLog(date = "2025.09.18", author = Author.BAEKJIHOON, issueNumber = 336, description = "반환값 구조 개선"),
      @ApiChangeLog(date = "2025.06.24", author = Author.BAEKJIHOON, issueNumber = 162, description = "회원정보 반환 Point<G2D> 오류 해결"),
      @ApiChangeLog(date = "2025.03.22", author = Author.WISEUNGJAE, issueNumber = 39, description = "회원정보 반환 로직 생성"),
  })
  @Operation(
      summary = "회원정보 반환",
      description = """
            ## 인증(JWT): **필요**
          
            ## 요청 파라미터 (없음)
          
            ## 반환값 (MemberResponse)
            - **`Member`**: JWT 의 유저 정보
            - **`MemberLocation`**: 해당 유저의 위치 정보
            - **`MemberItemCategories`**: 해당 유저의 선호 카테고리
            
            ## 반홥값 예시
            ```
            {
              "member": {
                "createdDate": "2025-09-18T11:02:03.125039",
                "updatedDate": "2025-09-18T11:03:59.498532",
                "memberId": "2d978675-0e37-4a6c-91f3-9866df0a3411",
                "email": "bjh59629@naver.com",
                "nickname": "한들한들강-1124",
                "socialPlatform": "KAKAO",
                "profileUrl": "https://example.com",
                "role": "ROLE_USER",
                "accountStatus": "ACTIVE_ACCOUNT",
                "isFirstLogin": true,
                "isItemCategorySaved": true,
                "isFirstItemPosted": false,
                "isMemberLocationSaved": true,
                "isRequiredTermsAgreed": false,
                "isMarketingInfoAgreed": false,
                "password": null,
                "latitude": null,
                "longitude": null,
                "totalLikeCount": 0
              },
              "memberLocation": {
                "createdDate": "2025-09-18T11:02:41.42268",
                "updatedDate": "2025-09-18T11:02:41.42268",
                "memberLocationId": "f8894eef-a0a2-4547-bed9-7fd2b10cd611",
                "siDo": "경기도",
                "siGunGu": "구리시",
                "eupMyoenDong": "교문동",
                "ri": "string",
                "longitude": 123.1,
                "latitude": 56.900000000000006
              },
              "memberItemCategories": [
                {
                  "createdDate": "2025-09-18T11:03:57.625048",
                  "updatedDate": "2025-09-18T11:03:57.625048",
                  "memberItemCategoryId": "0ddfdcb4-30a4-4654-9073-fe8bcfa3f0ff",
                  "itemCategory": "WOMEN_CLOTHING"
                },
                {
                  "createdDate": "2025-09-18T11:03:57.627648",
                  "updatedDate": "2025-09-18T11:03:57.627648",
                  "memberItemCategoryId": "2503a67c-92a0-46ee-b4a5-b309b79c1b40",
                  "itemCategory": "MEN_CLOTHING"
                },
                {
                  "createdDate": "2025-09-18T11:03:57.627743",
                  "updatedDate": "2025-09-18T11:03:57.627743",
                  "memberItemCategoryId": "c025195d-1874-4c1b-819f-f3050805abc2",
                  "itemCategory": "SHOES"
                }
              ]
            }
            ```          
          
            ## 에러코드
            - **`INVALID_MEMBER`**: 유효하지 않은 회원 정보입니다.
          """
  )
  ResponseEntity<MemberResponse> getMemberInfo(CustomUserDetails customUserDetails);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2025.04.04", author = Author.BAEKJIHOON, issueNumber = 69, description = "@SoftDelete 제거 및 수동 softDelete 구현"),
      @ApiChangeLog(date = "2025.03.27", author = Author.BAEKJIHOON, issueNumber = 69, description = "회원 탈퇴 init"),
  })
  @Operation(
      summary = "회원 탈퇴",
      description = """
            ## 인증(JWT): **필요**
          
            ## 요청 파라미터 (없음)

            ## 처리 로직
            1. 회원 관련 데이터 삭제 (이메일, 닉네임 초기화)
            2. 회원이 등록한 물품 및 관련 데이터 삭제
            3. 인증 관련 토큰 비활성화
            4. 회원 정보 삭제 (소프트 딜리트)
          
            ## 반환값
            - 성공 시 상태코드 200 (OK)와 빈 응답 본문
          
            ## 에러코드
            - **`INVALID_MEMBER`**: 유효하지 않은 회원 정보입니다.
            - **`UNAUTHORIZED`**: 인증이 필요한 요청입니다.
          """
  )
  ResponseEntity<Void> deleteMember(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute MemberRequest request,
      HttpServletRequest httpServletRequest);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2025.11.28", author = Author.BAEKJIHOON, issueNumber = 411, description = "totalLikeCount 반환값 추가"),
      @ApiChangeLog(date = "2025.09.18", author = Author.BAEKJIHOON, issueNumber = 336, description = "반환값 구조 개선"),
      @ApiChangeLog(date = "2025.06.24", author = Author.BAEKJIHOON, issueNumber = 162, description = "필수 이용약관 동의 여부 입력 파라미터 제거"),
      @ApiChangeLog(date = "2025.06.23", author = Author.BAEKJIHOON, issueNumber = 162, description = "이용약관 동의 API 요청값 & 반환값 수정 및 필수 이용약관 동의 여부 검증 로직 추가"),
      @ApiChangeLog(date = "2025.05.26", author = Author.WISEUNGJAE, issueNumber = 123, description = "이용약관 동의 여부 확인"),
  })
  @Operation(
      summary = "이용약관 동의",
      description = """
          ## 인증(JWT): **필요**
          
          ## 요청 파라미터 (MemberRequest)
          - **`isMarketingInfoAgreed`** : 마케팅 정보 수신 동의 여부
          
          ## 반환값 (MemberResponse)
          - `MemberResponse` : 동의 상태가 반영된 회원 정보
          
          ## 반환값 예시
          ```
          {
            "member": {
              "createdDate": "2025-09-18T11:02:03.125039",
              "updatedDate": "2025-09-18T11:12:32.882105",
              "memberId": "2d978675-0e37-4a6c-91f3-9866df0a3411",
              "email": "bjh59629@naver.com",
              "nickname": "한들한들강-1124",
              "socialPlatform": "KAKAO",
              "profileUrl": "https://example.com",
              "role": "ROLE_USER",
              "accountStatus": "ACTIVE_ACCOUNT",
              "isFirstLogin": true,
              "isItemCategorySaved": true,
              "isFirstItemPosted": false,
              "isMemberLocationSaved": true,
              "isRequiredTermsAgreed": true,
              "isMarketingInfoAgreed": true,
              "password": null,
              "latitude": null,
              "longitude": null,
              "totalLikeCount": 0
            },
            "memberLocation": null,
            "memberItemCategories": null
          }
          ```
          """
  )
  ResponseEntity<MemberResponse> termsAgreement(
      CustomUserDetails customUserDetails,
      MemberRequest request
  );

  @ApiChangeLogs({
      @ApiChangeLog(date = "2025.11.09", author = Author.KIMNAYOUNG, issueNumber = 392, description = "탐색 범위 설정"),
  })
  @Operation(
      summary = "탐색 범위 설정",
      description = """
            ## 인증(JWT): **필요**
          
            ## 요청 파라미터 (MemberRequest)
            - **`searchRadiusInMeters`**: 탐색 범위 (단위: 미터) (double)
          
            ## 반환값
            성공시 : 201 CREATED
          """
  )
  ResponseEntity<Void> saveSearchRadius(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute MemberRequest request
  );

  @ApiChangeLogs({
      @ApiChangeLog(date = "2025.11.18", author = Author.KIMNAYOUNG, issueNumber = 407, description = "회원 프로필 변경"),
  })
  @Operation(
      summary = "회원 프로필 변경",
      description = """
            ## 인증(JWT): **필요**

            ## 요청 파라미터 (MemberRequest)
            - **`nickname`**: 닉네임 (String)
            - **`profileUrl`**: 프로필 이미지 url (String)

            ## 반환값
            성공시 : 201 CREATED

            ## 에러코드
            - **`DUPLICATE_NICKNAME`**: 이미 사용 중인 닉네임입니다.

            ## 설명
            - 닉네임과 프로필 이미지 URL 중 null이 아닌 값에 대해서만 업데이트
            - api/image/upload 를 통해 이미지 업로드 후 반환된 URL을 profileUrl로 설정
          """
  )
  ResponseEntity<Void> updateMemberProfile(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute MemberRequest request
  );

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.02.01", author = Author.SUHSAECHAN, issueNumber = 474, description = "차단된 회원 목록에 locationAddress 필드 추가"),
      @ApiChangeLog(date = "2026.01.03", author = Author.WISEUNGJAE, issueNumber = 428, description = "회원 차단 기능 구현"),
  })
  @Operation(
      summary = "본인이 차단한 회원 조회",
      description = """
            ## 인증(JWT): **필요**

            ## 반환값 (MemberResponse)
            성공시 : 200 OK
          - `members` : 차단된 멤버 리스트
            - 각 Member에 `locationAddress` 포함 (예: "서울특별시 광진구 화양동")

            ## 설명
            - 본인이 차단한 회원 리스트 반환
            - 상대방이 나를 차단한 경우는 포함되지 않음
          """
  )
  public ResponseEntity<MemberResponse> getBlockedMembers(
      @AuthenticationPrincipal CustomUserDetails customUserDetails);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.01.03", author = Author.WISEUNGJAE, issueNumber = 428, description = "회원 차단 기능 구현"),
  })
  @Operation(
      summary = "회원 차단",
      description = """
            ## 인증(JWT): **필요**
          
            ## 요청 파라미터 (MemberRequest)
            - **`blockTargetMemberId`**: 차단 대상 회원 ID (UUID)
          
            ## 반환값
            성공시 : 201 CREATED
          
            ## 에러코드
            - **`CANNOT_BLOCK_SELF`**: 자기 자신을 차단할 수 없습니다.
            - **`ALREADY_BLOCKED`**: 이미 차단한 회원입니다.
            - **`MEMBER_NOT_FOUND`**: 회원을 찾을 수 없습니다.
          """
  )
  public ResponseEntity<Void> postBlockMember(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute MemberRequest request);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.01.03", author = Author.WISEUNGJAE, issueNumber = 428, description = "회원 차단 기능 구현"),
  })
  @Operation(
      summary = "회원 차단 해제",
      description = """
            ## 인증(JWT): **필요**
          
            ## 요청 파라미터 (MemberRequest)
            - **`blockTargetMemberId`**: 차단 대상 회원 ID (UUID)
          
            ## 반환값
            성공시 : 204 NO CONTENT

            ## 설명
            - 차단 해제 시, 차단 기록이 삭제됨 (hard delete)
          """
  )
  public ResponseEntity<Void> deleteBlockedMember(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute MemberRequest request);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.02.01", author = Author.SUHSAECHAN, issueNumber = 474, description = "isBlocked, locationAddress 필드 추가"),
      @ApiChangeLog(date = "2026.01.04", author = Author.SUHSAECHAN, issueNumber = 432, description = "타인 회원 프로필 조회 API 추가"),
  })
  @Operation(
      summary = "타인 회원 프로필 조회",
      description = """
            ## 인증(JWT): **필요**

            ## 요청 파라미터 (MemberRequest)
            - **`memberId`**: 조회할 회원 ID (UUID)

            ## 반환값 (MemberResponse)
            - **`Member`**: 조회된 회원 정보
              - `isBlocked`: 현재 로그인한 사용자가 해당 회원을 차단했는지 여부 (boolean)
              - `locationAddress`: 회원의 위치 주소 (예: "서울특별시 광진구 화양동")
            - **`MemberLocation`**: 해당 회원의 위치 정보
            - **`MemberItemCategories`**: 해당 회원의 선호 카테고리

            ## 반환값 예시
            ```
            {
              "member": {
                "createdDate": "2025-09-18T11:02:03.125039",
                "updatedDate": "2025-09-18T11:03:59.498532",
                "memberId": "2d978675-0e37-4a6c-91f3-9866df0a3411",
                "email": "bjh59629@naver.com",
                "nickname": "한들한들강-1124",
                "socialPlatform": "KAKAO",
                "profileUrl": "https://example.com",
                "role": "ROLE_USER",
                "accountStatus": "ACTIVE_ACCOUNT",
                "isFirstLogin": true,
                "isItemCategorySaved": true,
                "isFirstItemPosted": false,
                "isMemberLocationSaved": true,
                "isRequiredTermsAgreed": false,
                "isMarketingInfoAgreed": false,
                "password": null,
                "latitude": null,
                "longitude": null,
                "totalLikeCount": 0,
                "isBlocked": false,
                "locationAddress": "경기도 구리시 교문동"
              },
              "memberLocation": {
                "createdDate": "2025-09-18T11:02:41.42268",
                "updatedDate": "2025-09-18T11:02:41.42268",
                "memberLocationId": "f8894eef-a0a2-4547-bed9-7fd2b10cd611",
                "siDo": "경기도",
                "siGunGu": "구리시",
                "eupMyoenDong": "교문동",
                "ri": "string",
                "longitude": 123.1,
                "latitude": 56.9
              },
              "memberItemCategories": [
                {
                  "createdDate": "2025-09-18T11:03:57.625048",
                  "updatedDate": "2025-09-18T11:03:57.625048",
                  "memberItemCategoryId": "0ddfdcb4-30a4-4654-9073-fe8bcfa3f0ff",
                  "itemCategory": "WOMEN_CLOTHING"
                }
              ]
            }
            ```

            ## 에러코드
            - **`MEMBER_NOT_FOUND`**: 회원을 찾을 수 없습니다.
          """
  )
  ResponseEntity<MemberResponse> getMemberProfile(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute MemberRequest request
  );
  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.02.06", author = Author.BAEKJIHOON, issueNumber = 482, description = "회원 알림 수신 on/off 설정"),
      @ApiChangeLog(date = "2026.01.13", author = Author.WISEUNGJAE, issueNumber = 446, description = "회원의 알림 수신 동의 여부 설정 API 추가"),
  })
  @Operation(
      summary = "회원의 알림 수신 동의 여부 설정",
      description = """
            ## 인증(JWT): **필요**
            
            ## 요청 바디 (MemberRequest)
            - 각 필드는 `Boolean`(Wrapper)로 처리되며,
              - **필드를 전달하지 않거나(null)** → **기존 설정 유지**
              - **true/false를 전달** → 해당 설정을 **즉시 변경(on/off)**
            - 변경이 필요한 항목만 선택적으로 보내면 된다.
            
            ### 지원 필드 (변경 가능한 항목)
            - `isMarketingInfoAgreed` : 마케팅 정보 수신 동의 여부 (true/false)
            - `isActivityNotificationAgreed` : 활동 알림 수신 동의 여부 (true/false)
            - `isChatNotificationAgreed` : 채팅 알림 수신 동의 여부 (true/false)
            - `isContentNotificationAgreed` : 콘텐츠 알림 수신 동의 여부 (true/false)
            - `isTradeNotificationAgreed` : 거래 알림 수신 동의 여부 (true/false)
            
            ### 요청 예시
            - 채팅 알림만 끄기
              - `isChatNotificationAgreed=false`
            - 활동/거래 알림만 켜기
              - `isActivityNotificationAgreed=true`
              - `isTradeNotificationAgreed=true`
          """
  )
  public ResponseEntity<MemberResponse> updateMemberNotificationAgreed(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute MemberRequest request);
}
