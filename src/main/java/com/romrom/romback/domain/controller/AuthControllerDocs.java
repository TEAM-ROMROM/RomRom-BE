package com.romrom.romback.domain.controller;

import com.romrom.romback.domain.object.dto.AuthRequest;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;

public interface AuthControllerDocs {

  @Operation(
      summary = "회원가입",
      description = """
                              
          이 API는 인증이 필요하지 않습니다.

          ### 요청 파라미터
          - **username** (String): 사용자 아이디 (중복 불가)
          - **password** (String): 사용자 비밀번호
          - **nickname** (String): 사용자 닉네임 (중복 불가)
                      
          ### 유의사항
          - `username`과 `nickname`은 고유해야 합니다.
          """
  )
  ResponseEntity<Void> signUp(AuthRequest request);

  @Operation(
      summary = "로그인",
      description = """
                              
          이 API는 인증이 필요하지 않습니다.

          ### 요청 파라미터
          - **username** (String): 사용자 아이디
          - **password** (String): 사용자 비밀번호
                      
          ### 유의사항
          - 개발자의 편의를 위해 만들어진 API 입니다.

          """
  )
  ResponseEntity<Void> signIn(AuthRequest request);

}
