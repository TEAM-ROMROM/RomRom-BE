package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminPushCampaignRequest;
import com.romrom.application.dto.AdminPushCampaignResponse;
import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.common.dto.Author;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;

public interface AdminPushCampaignControllerDocs {

  String CAMPAIGN_INPUT_DESCRIPTION = """
      ## 요청 파라미터 (AdminPushCampaignRequest, multipart/form-data)
      - **`pushTitle`** (필수, 최대 100자) / **`pushBody`** (필수, 최대 1000자)
      - **`pushImageUrl`** (선택): `https://` 이미지
      - **`pushDeepLink`** (선택): `romrom://notice/detail?announcementId={UUID}`, `romrom://item/detail?itemId={UUID}`, 그 외 `romrom://`·`https://`
      - **`purpose`**: `INFORMATION`(기본, 콘텐츠 알림 동의자 푸시) / `ADVERTISEMENT`(마케팅 수신 동의자만, 제목 "(광고) "·본문 수신거부 안내 자동, 21:00~08:00 금지)
      - **`targetType`**: `ALL`(활성 회원) / `SEGMENT` / `MEMBERS`
      - SEGMENT 조건 (입력한 것만): **`deviceType`**(ANDROID·IOS), **`isMarketingAgreed`**, **`activeWithinDays`**(1~3650), **`inactiveForDays`**(1~3650), **`joinedFromDate`**·**`joinedToDate`**(yyyy-MM-dd), **`siDo`**(시·도 앞부분)
      - MEMBERS: **`memberIdentifiers`** 회원 ID·이메일·닉네임 (줄바꿈·쉼표 구분, 최대 1000개)
      - 대상 공통: 탈퇴 제외, ALL·SEGMENT 는 정지·관리자 계정도 제외
      """;

  String CAMPAIGN_ERROR_DESCRIPTION = """
      ## 에러코드
      - **`INVALID_PUSH_CAMPAIGN_CONTENT`**: 제목·본문 누락 또는 길이 초과
      - **`INVALID_PUSH_IMAGE_URL`**: 이미지가 https:// 아님
      - **`INVALID_PUSH_DEEP_LINK`**: 링크 형식 오류 (공지·물품 ID 가 UUID 아님 포함)
      - **`INVALID_PUSH_CAMPAIGN_TARGET`**: 대상 누락, 일수 범위·가입일 역전, 식별자 0개·1000개 초과
      - **`INVALID_REQUEST`**: 날짜 형식 오류
      """;

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 874, description = "푸시 캠페인 미리보기(dry run) API 추가"),
  })
  @Operation(summary = "푸시 미리보기 (발송 없음)", description = "## 인증: **관리자**\n\n" + CAMPAIGN_INPUT_DESCRIPTION + """

      ## 반환값 (AdminPushCampaignResponse)
      - **`targetMemberCount`**: 조건에 맞는 회원 수 (알림함 기록 대상)
      - **`pushConsentedMemberCount`**: 그중 수신 동의 회원 수 (방해금지는 발송 시점에 판정)
      - **`pushTokenCount`**: 수신 동의 회원의 토큰 수 (SEGMENT 기기 조건이면 해당 기기만)
      - **`displayTitle`**, **`displayBody`**: 실제 표시 문구 (광고 표시 포함)
      """ + CAMPAIGN_ERROR_DESCRIPTION)
  ResponseEntity<AdminPushCampaignResponse> previewCampaign(AdminPushCampaignRequest request, CustomUserDetails principal);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 874, description = "푸시 테스트 발송 API 추가 (관리자 본인 토큰)"),
  })
  @Operation(summary = "푸시 테스트 발송", description = """
      ## 인증: **관리자**

      ## 요청 파라미터
      - 미리보기와 같은 내용 필드 (`pushTitle`, `pushBody`, `pushImageUrl`, `pushDeepLink`, `purpose`), 대상은 무시

      ## 동작 설명
      - 로그인한 관리자 계정에 등록된 FCM 토큰으로만 바로 발송 (targetType `TEST`, 알림함 기록 없음)
      - 수신 동의·방해금지·광고 야간 제한 미적용 (관리자 확인용)

      ## 반환값 (AdminPushCampaignResponse)
      - **`pushCampaign`**: 발송 결과가 반영된 캠페인 (`sentCount`, `failureCount`, `removedTokenCount`)

      ## 에러코드
      - **`PUSH_TEST_TOKEN_NOT_FOUND`**: 관리자 계정에 토큰 없음 (앱에서 관리자 계정으로 로그인 필요)
      - 그 외 내용 검증 오류는 미리보기와 같음
      """)
  ResponseEntity<AdminPushCampaignResponse> sendTestCampaign(AdminPushCampaignRequest request, CustomUserDetails principal);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 874, description = "푸시 캠페인 즉시·예약 발송 API 추가"),
  })
  @Operation(summary = "푸시 발송·예약", description = "## 인증: **관리자**\n\n" + CAMPAIGN_INPUT_DESCRIPTION + """
      - **`scheduledAt`** (선택): 예약 시각 `yyyy-MM-dd'T'HH:mm` (KST, 미입력 즉시 발송)

      ## 동작 설명
      - 즉시: 저장 후 바로 비동기 발송 (응답 시점 상태 SENDING 또는 SCHEDULED)
      - 예약: 1분 주기 스케줄러가 시각이 된 캠페인을 선점(SCHEDULED > SENDING 조건부 갱신)해 1회만 발송
      - 대상 회원 1000명씩 알림함 기록, 수신 동의·방해금지 아닌 회원만 푸시 (500건 묶음)
      - 광고성은 실행 시점에도 야간이면 발송하지 않고 FAILED + 사유

      ## 반환값 (AdminPushCampaignResponse)
      - **`pushCampaign`**: 생성된 캠페인
      """ + CAMPAIGN_ERROR_DESCRIPTION + """
      - **`INVALID_PUSH_CAMPAIGN_SCHEDULE`**: 예약 시각이 현재 이전
      - **`ADVERTISEMENT_PUSH_NIGHT_RESTRICTED`**: 광고성을 21:00~08:00에 즉시 발송·예약
      """)
  ResponseEntity<AdminPushCampaignResponse> createCampaign(AdminPushCampaignRequest request, CustomUserDetails principal);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 874, description = "푸시 캠페인 목록 API 추가"),
  })
  @Operation(summary = "푸시 캠페인 목록", description = """
      ## 인증: **관리자**

      ## 요청 파라미터
      - **`pageNumber`** (기본 0), **`pageSize`** (기본 20, 최대 100)

      ## 반환값 (AdminPushCampaignResponse)
      - **`pushCampaigns`**: 최근 생성순 (공지 자동 발송·테스트 발송 포함, targetType 으로 구분)
        - 주요 필드: `status`(SCHEDULED·SENDING·COMPLETED·FAILED·CANCELED), `targetMemberCount`, `sentCount`, `failureCount`, `removedTokenCount`, `openedCount`, `failureReason`
      - **`totalPages`**, **`totalElements`**, **`currentPage`**
      """)
  ResponseEntity<AdminPushCampaignResponse> getCampaigns(AdminPushCampaignRequest request);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 874, description = "푸시 캠페인 상세 API 추가"),
  })
  @Operation(summary = "푸시 캠페인 상세", description = """
      ## 인증: **관리자**

      ## 요청 파라미터
      - **`pushCampaignId`** (필수)

      ## 반환값
      - **`pushCampaign`**: 캠페인 (`targetFilter` JSON 포함)

      ## 에러코드
      - **`PUSH_CAMPAIGN_NOT_FOUND`**
      """)
  ResponseEntity<AdminPushCampaignResponse> getCampaignDetail(AdminPushCampaignRequest request);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 874, description = "예약 푸시 취소 API 추가"),
  })
  @Operation(summary = "예약 푸시 취소", description = """
      ## 인증: **관리자**

      ## 요청 파라미터
      - **`pushCampaignId`** (필수)

      ## 반환값
      - **`pushCampaign`**: 취소된 캠페인 (status `CANCELED`)

      ## 에러코드
      - **`PUSH_CAMPAIGN_NOT_FOUND`**
      - **`PUSH_CAMPAIGN_NOT_CANCELABLE`**: 이미 발송 시작·완료·취소된 캠페인
      """)
  ResponseEntity<AdminPushCampaignResponse> cancelCampaign(AdminPushCampaignRequest request, CustomUserDetails principal);
}
