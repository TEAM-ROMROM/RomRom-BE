package com.romrom.web.controller.api;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.common.dto.Author;
import com.romrom.report.dto.ReportRequest;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;
public interface ReportControllerDocs {


  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 858, description = "신고 시 신고자 숨김 물품 저장 > 신고자 홈 피드에서 영구 제외"),
      @ApiChangeLog(date = "2025.07.09", author = Author.WISEUNGJAE, issueNumber = 195, description = "물품 신고 API 구현"),
  })
  @Operation(
      summary = "아이템 신고",
      description = """
    ## 인증(JWT): **필요**
    
    ## 요청 파라미터 (ReportRequest)
    - **`itemId`**: 신고할 물품 ID
    - **`itemReportReasons`**: 물품 신고 사유 코드들 (복수 선택 가능)
    - **`extraComment`**: 기타 입력 내용 (선택)

    ## 신고 사유 코드 매핑표
    - **1**: 허위 정보/사기 의심
    - **2**: 불법·금지 물품
    - **3**: 부적절한 컨텐츠 (욕설·폭력 등)
    - **4**: 스팸·광고
    - **5**: 기타 (extraComment 사용)

    ## 동작
    - 신고 저장과 함께 신고자 기준 숨김 물품(다시 보지 않기와 같은 저장) 기록 > 이후 홈 피드 조회에서 제외 (본인 물품·이미 숨김이면 생략)

    ## 에러코드
    - **`ITEM_NOT_FOUND`**: 해당 물품을 찾을 수 없습니다.
    - **`DUPLICATE_REPORT`**: 같은 물품을 여러 번 신고할 수 없습니다.
    - **`TOO_LONG_EXTRA_COMMENT`**: 기타 의견의 글자 수 제한을 초과했습니다.
    - **`NULL_EXTRA_COMMENT`**: 기타 의견을 빈 값으로 요청할 수 없습니다.
    - **`MEMBER_NOT_FOUND`**: 회원을 찾을 수 없습니다.
    """
  )
  public ResponseEntity<Void> reportItem(CustomUserDetails customUserDetails, ReportRequest request);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.02.09", author = Author.SUHSAECHAN, issueNumber = 489, description = "회원 신고 API 구현"),
  })
  @Operation(
      summary = "회원 신고",
      description = """
    ## 인증(JWT): **필요**

    ## 요청 파라미터 (ReportRequest)
    - **`targetMemberId`**: 신고할 회원 ID
    - **`memberReportReasons`**: 회원 신고 사유 코드들 (복수 선택 가능)
    - **`extraComment`**: 기타 입력 내용 (선택)

    ## 신고 사유 코드 매핑표
    - **1**: 비매너/욕설/혐오/성적 발언
    - **2**: 사기 의심/거래 금지 물품
    - **3**: 물건 상태 불일치/허위 매물
    - **4**: 노쇼/약속 불이행
    - **5**: 기타(직접입력)

    ## 에러코드
    - **`MEMBER_NOT_FOUND`**: 회원을 찾을 수 없습니다.
    - **`SELF_REPORT`**: 자기 자신을 신고할 수 없습니다.
    - **`DUPLICATE_MEMBER_REPORT`**: 같은 회원을 여러 번 신고할 수 없습니다.
    - **`TOO_LONG_EXTRA_COMMENT`**: 기타 의견의 글자 수 제한을 초과했습니다.
    - **`NULL_EXTRA_COMMENT`**: 기타 의견을 빈 값으로 요청할 수 없습니다.
    """
  )
  public ResponseEntity<Void> reportMember(CustomUserDetails customUserDetails, ReportRequest request);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 860, description = "채팅 메시지 신고 API 구현"),
  })
  @Operation(
      summary = "채팅 메시지 신고",
      description = """
    ## 인증(JWT): **필요**

    ## 요청 파라미터 (ReportRequest)
    - **`chatRoomId`**: 메시지가 있는 채팅방 ID
    - **`chatMessageId`**: 신고할 메시지 ID (상대가 보낸 TEXT·IMAGE·LOCATION 메시지)
    - **`memberReportReasons`**: 신고 사유 코드들 (회원 신고 사유와 같은 코드, 복수 선택 가능)
    - **`extraComment`**: 기타 입력 내용 (사유 5 선택 시 필수, 1000자 이하)

    ## 신고 사유 코드 매핑표
    - **1**: 비매너/욕설/혐오/성적 발언
    - **2**: 사기 의심/거래 금지 물품
    - **3**: 물건 상태 불일치/허위 매물
    - **4**: 노쇼/약속 불이행
    - **5**: 기타(직접입력)

    ## 동작
    - 신고 시점의 메시지 원문(삭제·숨김이어도 원문)을 사본으로 저장하고 관리자 알림 메일(채팅 메시지 신고)을 보냅니다.

    ## 에러코드
    - **`INVALID_REQUEST`**: 필수값 누락, 모르는 사유 코드, 시스템·거래 안내 메시지
    - **`CHATROOM_NOT_FOUND`** / **`NOT_CHATROOM_MEMBER`**: 채팅방 없음 / 멤버 아님
    - **`CHAT_MESSAGE_NOT_FOUND`**: 메시지 없음 또는 다른 방 메시지
    - **`SELF_REPORT`**: 내 메시지는 신고할 수 없습니다.
    - **`DUPLICATE_CHAT_MESSAGE_REPORT`**: 같은 메시지를 여러 번 신고할 수 없습니다.
    - **`TOO_LONG_EXTRA_COMMENT`** / **`NULL_EXTRA_COMMENT`**: 기타 의견 길이 초과 / 누락
    """
  )
  public ResponseEntity<Void> reportChatMessage(CustomUserDetails customUserDetails, ReportRequest request);
}
