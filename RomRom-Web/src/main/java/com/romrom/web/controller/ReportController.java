package com.romrom.web.controller;


import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.report.dto.ReportRequest;
import com.romrom.report.service.ReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import me.suhsaechan.suhlogger.annotation.LogMonitor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(
    name = "신고 관련 API",
    description = "신고(아이템 등) 관련 API 제공"
)
@RequestMapping("/api/report")
public class ReportController implements ReportControllerDocs {

  private final ReportService reportService;

  @Override
  @PostMapping(value = "/item/post", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<Void> reportItem(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute ReportRequest request) {
    request.setMember(customUserDetails.getMember());
    reportService.createReport(request);
    return ResponseEntity.ok().build();
  }


}
