package com.romrom.romback.domain.controller;

import com.romrom.romback.domain.object.constant.Author;
import com.romrom.romback.domain.object.dto.AuthRequest;
import com.romrom.romback.global.docs.ApiChangeLog;
import com.romrom.romback.global.docs.ApiChangeLogs;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;

public interface AuthControllerDocs {

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.02.10",
          author = Author.SUHSAECHAN,
          issueNumber = 12,
          description = "로그인 DOCS 개선"
      ),
      @ApiChangeLog(
          date = "2025.02.09",
          author = Author.BAEKJIHOON,
          issueNumber = 4,
          description = "회워가입 API 구현"
      )
  })
  @Operation(
      summary = "회원가입",
      description = """
          ## 인증(JWT): **불필요**

          ## 참고사항
          - **`username`**: 중복이 불가능합니다.
          - **`nickname`**: 중복이 불가능합니다.

          ## 요청 파라미터 (AuthRequest)
          - **`username`**: 사용자 아이디
          - **`password`**: 사용자 비밀번호
          - **`nickname`**: 사용자 닉네임

          ## 반환값
          - **없음**

          ## 에러코드
          - **`DUPLICATE_USERNAME`**: 이미 가입된 아이디입니다.
          """
  )
  ResponseEntity<Void> signUp(AuthRequest request);

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.02.10",
          author = Author.SUHSAECHAN,
          issueNumber = 12,
          description = "로그인 DOCS 개선"
      ),
      @ApiChangeLog(
          date = "2025.02.9",
          author = Author.BAEKJIHOON,
          issueNumber = 4,
          description = "로그인 API 구현"
      )
  })
  @Operation(
      summary = "로그인",
      description = """
          ## 인증(JWT): **불필요**

          ## 참고사항
          - 개발자의 편의를 위해 만들어진 API 입니다.

          ## 요청 파라미터 (AuthRequest)
          - **`username`**: 사용자 아이디
          - **`password`**: 사용자 비밀번호

          ## 반환값
          - **없음**
          """
  )
  ResponseEntity<Void> signIn(AuthRequest request);

}
