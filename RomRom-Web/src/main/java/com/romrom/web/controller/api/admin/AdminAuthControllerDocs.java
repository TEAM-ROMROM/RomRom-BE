package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;

public interface AdminAuthControllerDocs {

    ResponseEntity<AdminResponse> login(AdminRequest request, HttpServletResponse response);

    ResponseEntity<Void> logout(String refreshTokenFromCookie, HttpServletResponse response);
}
