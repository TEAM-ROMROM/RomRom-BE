package com.romrom.web.controller;

import com.romrom.application.service.TestRequest;
import com.romrom.application.service.TestResponse;
import com.romrom.application.service.TestService;
import com.romrom.common.constant.ItemStatus;
import com.romrom.common.dto.Author;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import me.suhsaechan.suhlogger.annotation.LogMonitor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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
  @PostMapping(value = "/sign-up", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<TestResponse> testSignUp(@ModelAttribute TestRequest request) {
    return ResponseEntity.ok(testService.testSignIn(request));
  }

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.05.23",
          author = Author.KIMNAYOUNG,
          issueNumber = 122,
          description = "Mock 사용자 생성"
      )
  })
  @Operation(
      summary = "Mock 사용자 생성",
      description = """
          ## 인증(JWT): **불필요**
          
          ## 요청 파라미터 
          - **`count`**: 생성할 Mock 사용자 수
          
          ## 반환값 (없음)
          
          """
  )
  @PostMapping(value = "/user")
  @LogMonitor
  public ResponseEntity<Void> createMockMember(@Schema(defaultValue = "20") Integer count) {
    testService.createMockMembers(count);
    return ResponseEntity.ok().build();
  }

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.07.25",
          author = Author.KIMNAYOUNG,
          issueNumber = 234,
          description = "거래 희망 위치 추가"
      ),
      @ApiChangeLog(
          date = "2025.05.23",
          author = Author.KIMNAYOUNG,
          issueNumber = 122,
          description = "Mock 물품 생성"
      )
  })
  @Operation(
      summary = "Mock 물품 생성",
      description = """
          ## 인증(JWT): **불필요**
          
          ## 요청 파라미터
          - **`count`**: 생성할 Mock 물품 수
          
          ## 반환값 (없음)
          
          """
  )
  @PostMapping(value = "/item")
  @LogMonitor
  public ResponseEntity<Void> createMockItem(@Schema(defaultValue = "20") Integer count) {
    testService.createMockItems(count);
    return ResponseEntity.ok().build();
  }
}
