package com.romrom.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@ToString
@AllArgsConstructor
@Getter
@Builder
@NoArgsConstructor
public class AdminResponse {
    
    private String accessToken;
    private String refreshToken;
    private String username;
    private String role;
    
}