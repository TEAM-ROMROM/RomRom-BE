package com.romrom.web.controller.api;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.common.dto.Author;
import com.romrom.member.dto.MemberRequest;
import com.romrom.member.dto.MemberResponse;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 회원 차단 API 문서: 차단 목록 조회, 차단, 차단 해제 (MemberControllerDocs 분리)
 */
public interface MemberBlockControllerDocs {

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
}
