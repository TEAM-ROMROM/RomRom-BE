package com.romrom.web.controller;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.common.dto.Author;
import com.romrom.item.dto.ItemImageRequest;
import com.romrom.item.dto.ItemImageResponse;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;

public interface ItemImageControllerDocs {

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.08.03",
          author = Author.KIMNAYOUNG,
          issueNumber = 243,
          description = "이미지 업로드 워크플로우 개선"
      )
  })
  @Operation(
      summary = "물품 사진 업로드",
      description = """
              ## 인증(JWT): **필요**
          
              ## 요청 파라미터 (ItemImageRequest)
              - **`itemImages`**: 물품 사진 목록
          
              ## 반환값 (ItemImageResponse)
              - **`itemImageUrls`**: 업로드된 물품 사진 URL 목록
          """
  )
  ResponseEntity<ItemImageResponse> uploadItemImages(
      CustomUserDetails customUserDetails,
      ItemImageRequest request
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
      summary = "물품 사진 삭제",
      description = """
              ## 인증(JWT): **필요**
          
              ## 요청 파라미터 (ItemImageRequest)
              - **`itemId`**: 물품 ID
              - **`filePaths`**: 삭제할 물품 사진 파일 경로 목록
          
              ## 반환값 (ItemImageResponse)
              - **`item`**: 물품
              - **`itemImageUrls`**: 삭제 후 남은 물품 사진 URL 목록
          """
  )
  ResponseEntity<ItemImageResponse> deleteItemImages(
      CustomUserDetails customUserDetails,
      ItemImageRequest request
  );
}
