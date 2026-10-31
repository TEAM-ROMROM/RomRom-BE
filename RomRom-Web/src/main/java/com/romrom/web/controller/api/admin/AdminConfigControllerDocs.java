package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.common.dto.Author;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;

public interface AdminConfigControllerDocs {

    ResponseEntity<AdminResponse> getAiConfig(AdminRequest request);

    ResponseEntity<AdminResponse> updateAiConfig(AdminRequest request);

    ResponseEntity<Void> reloadCache(AdminRequest request);

    ResponseEntity<AdminResponse> getAppVersionConfig(AdminRequest request);

    ResponseEntity<AdminResponse> updateAppVersionConfig(AdminRequest request);

    ResponseEntity<AdminResponse> getUgcFilterConfig(AdminRequest request);

    ResponseEntity<AdminResponse> updateUgcFilterConfig(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.05.02", author = Author.SUHSAECHAN, issueNumber = 673, description = "서버 점검 모드 조회 API 구현"),
    })
    @Operation(
        summary = "서버 점검 모드 설정 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 반환값 (AdminResponse)
        - **`maintenanceEnabled`**: 점검 모드 활성화 여부 ("true"/"false")
        - **`maintenanceMessage`**: 점검 안내 메시지
        - **`maintenanceEndTime`**: 점검 예상 종료 시간 (ISO 8601, 없으면 빈 문자열)
        """
    )
    ResponseEntity<AdminResponse> getMaintenanceConfig(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.05.02", author = Author.SUHSAECHAN, issueNumber = 673, description = "서버 점검 모드 업데이트 API 구현"),
    })
    @Operation(
        summary = "서버 점검 모드 설정 업데이트",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`maintenanceEnabled`** (String, 선택): "true" 또는 "false"
        - **`maintenanceMessage`** (String, 선택): 점검 안내 메시지
        - **`maintenanceEndTime`** (String, 선택): 점검 예상 종료 시간 (ISO 8601, 예: 2026-05-02T15:00:00)

        ## 동작 설명
        - null인 필드는 무시하고 기존 설정 유지
        - maintenanceEnabled를 "true"로 설정하면 즉시 점검 모드 활성화
        - 점검 중에는 /api/admin/**, /api/app/version/check, /actuator/** 외 모든 API 503 반환

        ## 에러코드
        - INVALID_REQUEST (400): maintenanceEnabled가 "true"/"false" 외의 값
        """
    )
    ResponseEntity<AdminResponse> updateMaintenanceConfig(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.05.28", author = Author.SUHSAECHAN, issueNumber = 733, description = "이미지 조건부 압축/업로드 병렬화 설정 조회 API 구현"),
    })
    @Operation(
        summary = "이미지 압축/업로드 설정 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 반환값 (AdminResponse)
        - **`imageCompressSkipContentType`**: 압축 스킵 대상 contentType (기본 image/webp)
        - **`imageCompressSkipMaxSizeBytes`**: 압축 스킵 최대 용량(byte, 기본 512000)
        - **`imageUploadParallelPoolSize`**: 업로드 병렬 스레드풀 크기 (기본 8, 재시작 반영)
        """
    )
    ResponseEntity<AdminResponse> getImageConfig(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.05.28", author = Author.SUHSAECHAN, issueNumber = 733, description = "이미지 조건부 압축/업로드 병렬화 설정 업데이트 API 구현"),
    })
    @Operation(
        summary = "이미지 압축/업로드 설정 업데이트",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data, 모두 선택)
        - **`imageCompressSkipContentType`** (String): 압축 스킵 대상 contentType
        - **`imageCompressSkipMaxSizeBytes`** (String): 압축 스킵 최대 용량(byte, 0 이상 정수)
        - **`imageUploadParallelPoolSize`** (String): 업로드 병렬 스레드풀 크기 (양의 정수, 재시작 반영)

        ## 동작 설명
        - null/빈 필드는 무시하고 기존 설정 유지
        - contentType/용량 변경은 Redis 캐시 즉시 반영, 풀 크기는 서버 재시작 시 반영

        ## 에러코드
        - INVALID_REQUEST (400): skipMaxSizeBytes가 음수/비정수, poolSize가 0이하/비정수
        """
    )
    ResponseEntity<AdminResponse> updateImageConfig(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.10.31", author = Author.SUHSAECHAN, issueNumber = 838, description = "채팅 추천 운영값 조회 API 추가"),
    })
    @Operation(
        summary = "채팅 추천 운영값 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 반환값 (AdminResponse, 지금 적용 중인 값: system_config > yml 기본값)
        - **`chatRecommendationModel`**: 추천 모델
        - **`chatRecommendationLlmCooldownSeconds`**: LLM 호출 쿨다운 (초)
        - **`chatRecommendationSameActionCooldownSeconds`**: 같은 동작 쿨다운 (초)
        - **`chatRecommendationCacheTtlSeconds`**: 추천 캐시 유지 (초)
        - **`chatRecommendationTradeCompletionInactivitySeconds`**: 거래 완료 제안까지 대화 없는 시간 (초)
        - **`chatRecommendationTradeCompletionRetryCooldownSeconds`**: 거래 완료 제안 재시도 쿨다운 (초)
        """
    )
    ResponseEntity<AdminResponse> getChatRecommendationConfig(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.10.31", author = Author.SUHSAECHAN, issueNumber = 838, description = "채팅 추천 운영값 수정 API 추가"),
    })
    @Operation(
        summary = "채팅 추천 운영값 수정",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data, 보낸 값만 변경)
        - **`chatRecommendationModel`** (String)
        - **`chatRecommendationLlmCooldownSeconds`**, **`chatRecommendationSameActionCooldownSeconds`**, **`chatRecommendationCacheTtlSeconds`**,
          **`chatRecommendationTradeCompletionInactivitySeconds`**, **`chatRecommendationTradeCompletionRetryCooldownSeconds`** (양의 정수, 초)

        ## 동작 설명
        - DB와 Redis 캐시에 함께 저장되어 재시작 없이 반영됩니다. yml에 추천 모델이 지정돼 있으면 모델은 yml 값이 우선합니다.

        ## 반환값
        - 변경 후 적용 값 (조회 API와 같음)

        ## 에러코드
        - INVALID_REQUEST (400): 숫자가 아니거나 0 이하
        """
    )
    ResponseEntity<AdminResponse> updateChatRecommendationConfig(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.10.31", author = Author.SUHSAECHAN, issueNumber = 838, description = "전체 시스템 설정 조회 API 추가 (비밀 값 가림)"),
    })
    @Operation(
        summary = "전체 시스템 설정 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 반환값 (AdminResponse)
        - **`systemConfigs`**: system_config 전체 (키 이름순). SMTP 비밀번호 같은 비밀 값은 `******`로 가림

        ## 동작 설명
        - 읽기 전용입니다. 값 변경은 설정 화면의 각 묶음(AI, 점검, 이미지 등)에서 합니다.
        """
    )
    ResponseEntity<AdminResponse> getAllSystemConfigs(AdminRequest request);
}
