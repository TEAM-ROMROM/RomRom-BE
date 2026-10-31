package com.romrom.web.controller.api.admin;

import com.romrom.auth.dto.CustomUserDetails;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import io.swagger.v3.oas.annotations.Operation;
import com.romrom.common.dto.Author;
import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;

public interface AdminAuthControllerDocs {

    ResponseEntity<AdminResponse> login(AdminRequest request, HttpServletResponse response);

    ResponseEntity<Void> logout(String refreshTokenFromCookie, HttpServletResponse response);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.10.31", author = Author.SUHSAECHAN, issueNumber = 838, description = "관리자 비밀번호 변경 API 추가"),
    })
    @Operation(
        summary = "관리자 비밀번호 변경",
        description = """
        ## 인증: **ROLE_ADMIN** (로그인한 관리자 본인)

        ## 요청 파라미터 (multipart/form-data)
        - **`currentPassword`** (String, 필수): 현재 비밀번호
        - **`newPassword`** (String, 필수): 새 비밀번호 (8자 이상, 현재와 달라야 함)

        ## 동작 설명
        - admin.yml 비밀번호는 최초 계정 생성 때만 쓰이므로, 변경 후에는 바뀐 비밀번호가 유지됩니다.
        - 요청 본문은 로그에 남기지 않습니다.

        ## 에러코드
        - ADMIN_CURRENT_PASSWORD_MISMATCH (400): 현재 비밀번호 불일치
        - ADMIN_NEW_PASSWORD_INVALID (400): 8자 미만이거나 현재 비밀번호와 같음
        """
    )
    ResponseEntity<AdminResponse> changePassword(AdminRequest request, CustomUserDetails principal);
}
