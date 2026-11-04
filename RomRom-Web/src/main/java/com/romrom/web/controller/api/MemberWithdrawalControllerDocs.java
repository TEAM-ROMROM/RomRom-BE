package com.romrom.web.controller.api;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.common.dto.Author;
import com.romrom.item.dto.TradeResponse;
import com.romrom.member.dto.MemberRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 회원 탈퇴 API 문서: 탈퇴, 탈퇴 전 진행 중 거래 조회 (MemberControllerDocs 분리, #862)
 */
public interface MemberWithdrawalControllerDocs {

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 862, description = "탈퇴 사유(withdrawalReason·withdrawalReasonDetail, 선택) 수집, 진행 중 거래 자동 CANCELED, 탈퇴 기록 저장"),
      @ApiChangeLog(date = "2025.04.04", author = Author.BAEKJIHOON, issueNumber = 69, description = "@SoftDelete 제거 및 수동 softDelete 구현"),
      @ApiChangeLog(date = "2025.03.27", author = Author.BAEKJIHOON, issueNumber = 69, description = "회원 탈퇴 init"),
  })
  @Operation(
      summary = "회원 탈퇴",
      description = """
            ## 인증(JWT): **필요**

            ## 요청 파라미터 (MemberRequest, 전부 선택 — 구버전 앱처럼 미전송해도 탈퇴 진행)
            - **`withdrawalReason`**: 탈퇴 사유 `RARELY_USED`, `NO_WANTED_ITEMS`, `UNSATISFYING_TRADE`, `INCONVENIENT_SERVICE`, `PRIVACY_CONCERN`, `OTHER`
            - **`withdrawalReasonDetail`**: 상세 사유 (앞뒤 공백 제거, 최대 500자)

            ## 처리 로직
            0. 진행 중 거래(PENDING·CHATTING·TRADE_COMPLETE_REQUESTED, 보낸·받은 요청 모두) CANCELED + 탈퇴 기록 저장 (사유 미전송이면 사유 null)
            1. 회원 관련 데이터 삭제 (이메일, 닉네임 초기화)
            2. 회원이 등록한 물품 및 관련 데이터 삭제
            3. 인증 관련 토큰 비활성화
            4. 회원 정보 삭제 (소프트 딜리트)

            ## 반환값
            - 성공 시 상태코드 200 (OK)와 빈 응답 본문

            ## 에러코드
            - **`TOO_LONG_WITHDRAWAL_REASON_DETAIL`**: 상세 사유 500자 초과
            - **`INVALID_MEMBER`**: 유효하지 않은 회원 정보입니다.
            - **`UNAUTHORIZED`**: 인증이 필요한 요청입니다.
          """
  )
  ResponseEntity<Void> deleteMember(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute MemberRequest request,
      HttpServletRequest httpServletRequest);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 862, description = "탈퇴 전 진행 중 거래 조회 신규"),
  })
  @Operation(
      summary = "탈퇴 전 진행 중 거래 조회",
      description = """
            ## 인증(JWT): **필요**

            ## 요청 파라미터
            - 없음 (JWT로 회원 식별)

            ## 반환값 (TradeResponse)
            - **`ongoingTradeRequestHistories`**: 탈퇴 시 취소되는 진행 중 거래 목록 (최신순)
              - 대상: 내가 보낸·받은 요청 중 `PENDING`, `CHATTING`, `TRADE_COMPLETE_REQUESTED`
              - 각 항목은 거래 요청 조회와 같은 형태 (`takeItem`, `giveItem`, `tradeStatus` 등)
            - 비어 있으면 진행 중 거래 없음

            ## 설명
            - 앱은 목록이 있으면 탈퇴 전 확인 안내를 띄우고, 확인 시 `/api/members/delete` 호출 (서버가 자동 취소)
          """
  )
  ResponseEntity<TradeResponse> getOngoingTradesBeforeWithdrawal(
      @AuthenticationPrincipal CustomUserDetails customUserDetails);
}
