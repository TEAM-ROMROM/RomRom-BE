package com.romrom.romback.domain.controller;

import com.romrom.romback.domain.object.constant.Author;
import com.romrom.romback.domain.object.dto.TestRequest;
import com.romrom.romback.domain.object.dto.TestResponse;
import com.romrom.romback.domain.service.TestService;
import com.romrom.romback.global.aspect.LogMonitoringInvocation;
import com.romrom.romback.global.docs.ApiChangeLog;
import com.romrom.romback.global.docs.ApiChangeLogs;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
    name = "개발자용 TEST API",
    description = "개발자 테스트 관련 API 제공"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/test")
public class TestController {
  private final TestService testService;

  // 테스트 계정 생성을 위한 API 입니다
  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.02.23",
          author = Author.SUHSAECHAN,
          issueNumber = 21,
          description = "테스트 회원가입 및 로그인 구현, 로직 완성"
      ),
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
          description = "가짜 회원가입 API 구현"
      )
  })
  @Operation(
      summary = "가짜 회원가입",
      description = """
          ## 인증(JWT): **불필요**
          
          ## 참고사항
          - 가짜 회원가입
          
          ## 요청 파라미터 (AuthRequest)
          
          ## 반환값
          - **`accessToken`**
          - **`refreshToken`**
          
          ## 에러코드
          - **`DUPLICATE_USERNAME`**: 이미 가입된 이메일입니다.
          """
  )
  @PostMapping(value = "/signup", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitoringInvocation
  public ResponseEntity<TestResponse> testSignUp(@ModelAttribute TestRequest request) {
    return ResponseEntity.ok(testService.testSignIn(request));
  }
}
