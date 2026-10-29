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
}
