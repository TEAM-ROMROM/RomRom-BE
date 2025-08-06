package com.romrom.web.controller;

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
      @ApiChangeLog(
          date = "2025.03.04",
          author = Author.SUHSAECHAN,
          issueNumber = 32,
          description = "파라미터 수정: memberProductCategories -> preferredCategories"
      ),
      @ApiChangeLog(
          date = "2025.02.23",
          author = Author.SUHSAECHAN,
          issueNumber = 32,
          description = "회원 선호 카테고리 저장 API 추가"
      )
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
      @ApiChangeLog(
          date = "2025.07.14",
          author = Author.BAEKJIHOON,
          issueNumber = 214,
          description = "사용자 위치정보 중복 저장 방지 (중복 저장 요청시 기존 정보 업데이트)"
      ),
      @ApiChangeLog(
          date = "2025.04.01",
          author = Author.SUHSAECHAN,
          issueNumber = 101,
          description = "요청 파라미터 삭제: fullAddress, roadAddress, ri에 대한 null값 허용"
      ),
      @ApiChangeLog(
          date = "2025.03.11",
          author = Author.BAEKJIHOON,
          issueNumber = 50,
          description = "사용자 위치인증 데이터 저장"
      )
  })
  @Operation(
      summary = "사용자 위치 데이터 저장",
      description = """
            ## 인증(JWT): **필요**
          
            ## 요청 파라미터 (MemberRequest)
            - **`longitude`**: 경도 (double)
            - **`latitude`**: 위도도 (double)
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
      @ApiChangeLog(
          date = "2025.06.24",
          author = Author.BAEKJIHOON,
          issueNumber = 162,
          description = "회원정보 반환 Point<G2D> 오류 해결"
      ),
      @ApiChangeLog(
          date = "2025.03.22",
          author = Author.WISEUNGJAE,
          issueNumber = 39,
          description = "회원정보 반환 로직 생성"
      )
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
          
            ## 에러코드
            - **`INVALID_MEMBER`**: 유효하지 않은 회원 정보입니다.
          """
  )
  ResponseEntity<MemberResponse> getMemberInfo(CustomUserDetails customUserDetails);

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.04.04",
          author = Author.BAEKJIHOON,
          issueNumber = 69,
          description = "@SoftDelete 제거 및 수동 softDelete 구현"
      ),
      @ApiChangeLog(
          date = "2025.03.27",
          author = Author.BAEKJIHOON,
          issueNumber = 69,
          description = "회원 탈퇴 init"
      )
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
      @ApiChangeLog(
          date = "2025.06.24",
          author = Author.BAEKJIHOON,
          issueNumber = 162,
          description = "필수 이용약관 동의 여부 입력 파라미터 제거"
      ),
      @ApiChangeLog(
          date = "2025.06.23",
          author = Author.BAEKJIHOON,
          issueNumber = 162,
          description = "이용약관 동의 API 요청값 & 반환값 수정 및 필수 이용약관 동의 여부 검증 로직 추가"
      ),
      @ApiChangeLog(
          date = "2025.05.26",
          author = Author.WISEUNGJAE,
          issueNumber = 123,
          description = "이용약관 동의 여부 확인"
      )
  })
  @Operation(
      summary = "이용약관 동의",
      description = """
          ## 인증(JWT): **필요**
          
          ## 요청 파라미터 (MemberRequest)
          - **`isMarketingInfoAgreed`** : 마케팅 정보 수신 동의 여부
          
          ## 반환값 (MemberResponse)
          - `MemberResponse` : 동의 상태가 반영된 회원 정보
          """
  )
  ResponseEntity<MemberResponse> termsAgreement(
      CustomUserDetails customUserDetails,
      MemberRequest request
  );
}
