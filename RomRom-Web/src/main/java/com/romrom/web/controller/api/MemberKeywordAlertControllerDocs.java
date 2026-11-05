package com.romrom.web.controller.api;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.common.dto.Author;
import com.romrom.member.dto.MemberKeywordAlertRequest;
import com.romrom.member.dto.MemberKeywordAlertResponse;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 키워드 알림 API 문서: 목록 조회, 등록, 삭제 (MemberControllerDocs 분리, #873)
 */
public interface MemberKeywordAlertControllerDocs {

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 873, description = "키워드 알림 목록 조회 추가"),
  })
  @Operation(
      summary = "키워드 알림 목록 조회",
      description = """
            ## 인증(JWT): **필요**

            ## 반환값 (MemberKeywordAlertResponse)
            성공시 : 200 OK
            - **`keywordAlerts`**: 등록한 키워드 목록 (최신 등록순)
              - **`keywordAlertId`**: 키워드 알림 ID (UUID, 삭제 시 사용)
              - **`keyword`**: 등록한 키워드 (앞뒤·연속 공백 정리, 입력한 대소문자 유지)
              - **`createdDate`**: 등록 일시
            - **`maximumKeywordAlertCount`**: 회원당 최대 등록 개수 (10)

            ```json
            {
              "keywordAlerts": [
                { "keywordAlertId": "2f1c...-uuid", "keyword": "닌텐도 스위치", "createdDate": "2026-11-05T13:40:36.478223" }
              ],
              "maximumKeywordAlertCount": 10
            }
            ```

            ## 설명
            - 키워드가 이름 또는 설명에 들어간 새 물품이 내 탐색 범위 안에 등록되면 알림(`KEYWORD_ITEM_REGISTERED`)을 받음
            - 키워드 등록 자체가 수신 동의 (별도 알림 설정 없음, 방해금지 시간은 적용)
          """
  )
  ResponseEntity<MemberKeywordAlertResponse> getKeywordAlerts(
      @AuthenticationPrincipal CustomUserDetails customUserDetails);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 873, description = "키워드 알림 등록 추가"),
  })
  @Operation(
      summary = "키워드 알림 등록",
      description = """
            ## 인증(JWT): **필요**

            ## 요청 파라미터 (MemberKeywordAlertRequest, multipart/form-data)
            - **`keyword`**: 등록할 키워드 (String)

            ## 정규화·검증
            - 앞뒤 공백 제거 > 연속 공백 1칸 > 소문자 기준으로 비교
            - 정규화 후 2자 이상 20자 이하
            - 같은 정규화 값(예: "Switch"와 " switch ")은 중복
            - 회원당 최대 10개

            ## 반환값 (MemberKeywordAlertResponse)
            성공시 : 200 OK (등록 후 최신 목록, 형식은 목록 조회와 같음)

            ## 에러코드 (errorMessage 는 사용자에게 그대로 표시)
            - **`INVALID_KEYWORD_ALERT_LENGTH`** (400): 키워드는 2자 이상 20자 이하로 입력해 주세요.
            - **`DUPLICATE_KEYWORD_ALERT`** (400): 이미 등록한 키워드예요.
            - **`KEYWORD_ALERT_LIMIT_EXCEEDED`** (400): 키워드는 최대 10개까지 등록할 수 있어요.
          """
  )
  ResponseEntity<MemberKeywordAlertResponse> postKeywordAlert(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute MemberKeywordAlertRequest request);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 873, description = "키워드 알림 삭제 추가"),
  })
  @Operation(
      summary = "키워드 알림 삭제",
      description = """
            ## 인증(JWT): **필요**

            ## 요청 파라미터 (MemberKeywordAlertRequest, multipart/form-data)
            - **`keywordAlertId`**: 삭제할 키워드 알림 ID (UUID)

            ## 반환값 (MemberKeywordAlertResponse)
            성공시 : 200 OK (삭제 후 최신 목록, 형식은 목록 조회와 같음)

            ## 에러코드
            - **`KEYWORD_ALERT_NOT_FOUND`** (404): 등록된 키워드를 찾을 수 없어요. (없거나 다른 회원의 키워드)

            ## 설명
            - 본인 키워드만 삭제 (hard delete)
          """
  )
  ResponseEntity<MemberKeywordAlertResponse> deleteKeywordAlert(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute MemberKeywordAlertRequest request);
}
