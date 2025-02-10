package com.romrom.romback.domain.authentication.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
@AllArgsConstructor
@Getter
@Setter
@Builder
public class SignInRequest {

    @NotBlank(message = "로그인 아이디를 입력하세요")
    @Schema(defaultValue = "example123")
    private String username;

    @NotBlank(message = "로그인 비밀번호를 입력하세요")
    @Schema(defaultValue = "pw12345")
    private String password;
}
