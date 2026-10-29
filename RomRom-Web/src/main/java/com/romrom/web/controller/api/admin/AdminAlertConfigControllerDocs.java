package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.common.dto.Author;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;

public interface AdminAlertConfigControllerDocs {

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.05.24", author = Author.BAEKJIHOON, issueNumber = 715, description = "AlertConfig 조회 API Swagger 문서 추가"),
    })
    @Operation(
        summary = "알림 설정 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 반환값 (AdminResponse)
        - **`alertEmail`**: 알림 수신 이메일
        - **`alertThrottleMinutes`**: 신고 알림 쓰로틀링 시간 (분)
        - **`mailSmtpHost`**: SMTP 서버 호스트
        - **`mailSmtpPort`**: SMTP 서버 포트
        - **`mailSmtpUsername`**: SMTP 발송 계정

        ## 요청 파라미터
        - 없음 (multipart/form-data 형식으로 빈 요청 전송)
        """
    )
    ResponseEntity<AdminResponse> getAlertConfig(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.05.24", author = Author.BAEKJIHOON, issueNumber = 715, description = "AlertConfig 업데이트 API Swagger 문서 추가"),
    })
    @Operation(
        summary = "알림 설정 업데이트",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`alertEmail`** (String, 선택): 알림 수신 이메일
        - **`alertThrottleMinutes`** (Integer, 선택): 신고 알림 쓰로틀링 시간 (분)
        - **`mailSmtpHost`** (String, 선택): SMTP 서버 호스트
        - **`mailSmtpPort`** (Integer, 선택): SMTP 서버 포트
        - **`mailSmtpUsername`** (String, 선택): SMTP 발송 계정
        - **`mailSmtpPassword`** (String, 선택): SMTP 발송 비밀번호

        ## 동작 설명
        - null인 필드는 무시하고 기존 설정 유지
        - 업데이트 후 MailSender 즉시 재로드 (변경된 SMTP 설정으로 실제 메일 발송)

        ## 에러코드
        - 없음 (null 필드는 조용히 무시)
        """
    )
    ResponseEntity<AdminResponse> updateAlertConfig(AdminRequest request);
}
