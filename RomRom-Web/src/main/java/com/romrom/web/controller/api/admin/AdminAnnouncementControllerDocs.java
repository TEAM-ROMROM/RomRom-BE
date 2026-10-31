package com.romrom.web.controller.api.admin;

import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import io.swagger.v3.oas.annotations.Operation;
import com.romrom.common.dto.Author;
import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import org.springframework.http.ResponseEntity;

public interface AdminAnnouncementControllerDocs {

    ResponseEntity<AdminResponse> getAnnouncements(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.10.31", author = Author.SUHSAECHAN, issueNumber = 838, description = "isPushNotificationEnabled=false면 전체 푸시 없이 저장"),
    })
    @Operation(
        summary = "공지 등록",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`announcementTitle`** (String, 필수), **`announcementContent`** (String, 필수)
        - **`isPushNotificationEnabled`** (Boolean, 기본값 true): false면 푸시 없이 앱 공지 목록에만 노출
        """
    )
    ResponseEntity<AdminResponse> createAnnouncement(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.10.31", author = Author.SUHSAECHAN, issueNumber = 838, description = "공지 수정 API 추가"),
    })
    @Operation(
        summary = "공지 수정",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`announcementId`** (UUID, 필수)
        - **`announcementTitle`** (String, 필수), **`announcementContent`** (String, 필수)

        ## 동작 설명
        - 제목·본문만 바꾸고 푸시는 다시 보내지 않습니다.

        ## 에러코드
        - INVALID_REQUEST (400): 없는 공지, 제목·본문 공백
        """
    )
    ResponseEntity<AdminResponse> updateAnnouncement(AdminRequest request);

    ResponseEntity<AdminResponse> deleteAnnouncement(AdminRequest request);
}
