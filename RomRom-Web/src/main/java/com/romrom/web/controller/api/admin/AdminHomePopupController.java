package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.application.service.AdminHomePopupService;
import lombok.RequiredArgsConstructor;
import me.suhsaechan.suhlogger.annotation.LogMonitor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 홈 팝업 API (#749): 목록, 등록, 수정, 삭제
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminHomePopupController implements AdminHomePopupControllerDocs {

  private final AdminHomePopupService adminHomePopupService;

  @Override
  @PostMapping(value = "/home-popups/list", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<AdminResponse> getHomePopups(@ModelAttribute AdminRequest request) {
    return ResponseEntity.ok(adminHomePopupService.getHomePopups(request));
  }

  @Override
  @PostMapping(value = "/home-popups/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<AdminResponse> createHomePopup(@ModelAttribute AdminRequest request) {
    return ResponseEntity.ok(adminHomePopupService.createHomePopup(request));
  }

  @Override
  @PostMapping(value = "/home-popups/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<AdminResponse> updateHomePopup(@ModelAttribute AdminRequest request) {
    return ResponseEntity.ok(adminHomePopupService.updateHomePopup(request));
  }

  @Override
  @PostMapping(value = "/home-popups/delete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<AdminResponse> deleteHomePopup(@ModelAttribute AdminRequest request) {
    return ResponseEntity.ok(adminHomePopupService.deleteHomePopup(request));
  }
}
