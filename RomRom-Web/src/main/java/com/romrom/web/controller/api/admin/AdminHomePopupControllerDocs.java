package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.common.dto.Author;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;

public interface AdminHomePopupControllerDocs {

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.10.31", author = Author.SUHSAECHAN, issueNumber = 749, description = "관리자 홈 팝업 목록 API 추가"),
  })
  @Operation(
      summary = "홈 팝업 목록",
      description = """
          ## 인증: **관리자**

          ## 요청 파라미터 (AdminRequest, multipart/form-data)
          - **`pageNumber`**: 페이지 번호 (기본 0)
          - **`pageSize`**: 페이지 크기 (기본 20)

          ## 반환값 (AdminResponse)
          - **`homePopups`**: 홈 팝업 목록 (priority 큰 순, 최근 등록순)
          - **`totalPages`**, **`totalElements`**, **`currentPage`**
          """
  )
  ResponseEntity<AdminResponse> getHomePopups(AdminRequest request);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.10.31", author = Author.SUHSAECHAN, issueNumber = 749, description = "관리자 홈 팝업 등록 API 추가"),
  })
  @Operation(
      summary = "홈 팝업 등록",
      description = """
          ## 인증: **관리자**

          ## 요청 파라미터 (AdminRequest, multipart/form-data)
          - **`homePopupTitle`** (필수, 최대 100자): 제목
          - **`homePopupImage`** (필수, 이미지 파일): 저장소 업로드 후 imageUrl로 저장
          - **`homePopupDescription`**: 설명
          - **`homePopupLinkUrl`**: `https://` 또는 `romrom://` 로 시작
          - **`homePopupPriority`**: 우선순위 (클수록 우선, 미입력 0)
          - **`isHomePopupActive`**: 활성 여부 (미입력 true)
          - **`homePopupStartDate`** / **`homePopupEndDate`**: `yyyy-MM-dd'T'HH:mm`, 미입력 제한 없음

          ## 반환값 (AdminResponse)
          - **`homePopup`**: 생성된 HomePopup 엔티티

          ## 에러코드
          - **`HOME_POPUP_IMAGE_REQUIRED`**: 이미지 미첨부
          - **`INVALID_HOME_POPUP_LINK_URL`**: 허용하지 않는 링크 형식
          - **`INVALID_HOME_POPUP_PERIOD`**: 종료 일시가 시작 일시보다 앞
          - **`INVALID_REQUEST`**: 제목 누락·100자 초과, 일시 형식 오류
          - **`FILE_UPLOAD_ERROR`**: 이미지 업로드 실패
          """
  )
  ResponseEntity<AdminResponse> createHomePopup(AdminRequest request);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.10.31", author = Author.SUHSAECHAN, issueNumber = 749, description = "관리자 홈 팝업 수정 API 추가"),
  })
  @Operation(
      summary = "홈 팝업 수정",
      description = """
          ## 인증: **관리자**

          ## 요청 파라미터 (AdminRequest, multipart/form-data)
          - **`homePopupId`** (필수)
          - 등록과 같은 필드를 **폼 전체 값으로 덮어씀** (빈 값 = 설명·링크 없음, 기간 제한 없음, 우선순위 0, 활성 true)
          - **`homePopupImage`**: 첨부 시에만 교체 (저장 성공 후 이전 이미지 파일 삭제), 미첨부 시 기존 이미지 유지

          ## 반환값 (AdminResponse)
          - **`homePopup`**: 수정된 HomePopup 엔티티

          ## 에러코드
          - **`HOME_POPUP_NOT_FOUND`**: 없는 팝업
          - 등록 API와 동일한 검증 에러
          """
  )
  ResponseEntity<AdminResponse> updateHomePopup(AdminRequest request);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.10.31", author = Author.SUHSAECHAN, issueNumber = 749, description = "관리자 홈 팝업 삭제 API 추가"),
  })
  @Operation(
      summary = "홈 팝업 삭제",
      description = """
          ## 인증: **관리자**

          ## 요청 파라미터 (AdminRequest, multipart/form-data)
          - **`homePopupId`** (필수)

          ## 반환값
          - 빈 AdminResponse (이미지 파일 삭제 실패는 경고 로그만)

          ## 에러코드
          - **`HOME_POPUP_NOT_FOUND`**: 없는 팝업
          """
  )
  ResponseEntity<AdminResponse> deleteHomePopup(AdminRequest request);
}
