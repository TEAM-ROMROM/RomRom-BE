package com.romrom.web.controller.api;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.common.dto.Author;
import com.romrom.storage.dto.StorageRequest;
import com.romrom.storage.dto.StorageResponse;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;

public interface StorageControllerDocs {

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.08.03",
          author = Author.KIMNAYOUNG,
          issueNumber = 243,
          description = "이미지 업로드 워크플로우 개선"
      )
  })
  @Operation(
      summary = "사진 업로드",
      description = """
          ## 인증(JWT): **필요**
          
          ## 요청 파라미터 (StorageRequest)
          - **`images`**: 사진 목록
          
          ## 반환값 (StorageResponse)
          - **`imageUrls`**: 업로드된 사진 URL 목록
          """
  )
  ResponseEntity<StorageResponse> uploadImages(
      CustomUserDetails customUserDetails,
      StorageRequest request
  );

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.08.03",
          author = Author.KIMNAYOUNG,
          issueNumber = 243,
          description = "이미지 업로드 워크플로우 개선"
      )
  })
  @Operation(
      summary = "사진 삭제",
      description = """
          ## 인증(JWT): **필요**
          
          ## 요청 파라미터 (StorageRequest)
          - **`imageUrls`**: 삭제할 사진 파일 경로 목록
          
          ## 반환값 (없음)
          """
  )
  ResponseEntity<Void> deleteImages(
      CustomUserDetails customUserDetails,
      StorageRequest request
  );
}
