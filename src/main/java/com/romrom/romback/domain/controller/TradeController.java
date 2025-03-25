package com.romrom.romback.domain.controller;

import com.romrom.romback.domain.object.dto.CustomUserDetails;
import com.romrom.romback.domain.object.dto.TradeRequest;
import com.romrom.romback.domain.object.dto.TradeResponse;
import com.romrom.romback.domain.service.TradeRequestService;
import com.romrom.romback.global.aspect.LogMonitoringInvocation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trade")
@RequiredArgsConstructor
@Tag(
    name = "거래 관련 API",
    description = "거래 요청 관련 API 제공"
)
public class TradeController {

  private final TradeRequestService tradeRequestService;

  @PostMapping(value = "/post", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitoringInvocation
  public ResponseEntity<Void> requestTrade(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute TradeRequest request) {
    request.setMember(customUserDetails.getMember());
    tradeRequestService.sendTradeRequest(request);
    return ResponseEntity.ok().build();
  }

  @PostMapping(value = "/delete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitoringInvocation
  public ResponseEntity<Void> cancelTradeRequest(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute TradeRequest request) {
    request.setMember(customUserDetails.getMember());
    tradeRequestService.cancelTradeRequest(request);
    return ResponseEntity.ok().build();
  }

  @PostMapping(value = "/get/received", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitoringInvocation
  public ResponseEntity<List<TradeResponse>> getReceivedTradeRequests(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute TradeRequest request) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(tradeRequestService.getReceivedTradeRequests(request));
  }

  @PostMapping(value = "/get/sent", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitoringInvocation
  public ResponseEntity<List<TradeResponse>> getSentTradeRequests(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute TradeRequest request) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(tradeRequestService.getSentTradeRequests(request));
  }
}
