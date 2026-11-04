package com.romrom.web.controller.api;

import com.romrom.application.service.TradeRequestChatRoomService;
import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.item.dto.TradeRequest;
import com.romrom.item.dto.TradeResponse;
import com.romrom.item.service.SentTradeRequestQueryService;
import com.romrom.item.service.TradeItemRecommendationService;
import com.romrom.item.service.TradeRequestService;
import com.romrom.item.service.TradeReviewService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import me.suhsaechan.suhlogger.annotation.LogMonitor;
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
    name = "거래 API",
    description = "거래 관련 API 제공"
)
public class TradeController implements TradeControllerDocs, TradeReviewControllerDocs {

  private final TradeRequestService tradeRequestService;
  private final TradeReviewService tradeReviewService;
  private final SentTradeRequestQueryService sentTradeRequestQueryService;
  private final TradeRequestChatRoomService tradeRequestChatRoomService;
  private final TradeItemRecommendationService tradeItemRecommendationService;

  @Override
  @PostMapping(value = "/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<TradeResponse> validateTradeBeforeMessaging(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute TradeRequest request) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(tradeRequestService.checkTradeRequest(request));
  }

  @Override
  @PostMapping(value = "/post", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<Void> requestTrade(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute TradeRequest request) {
    request.setMember(customUserDetails.getMember());
    tradeRequestService.sendTradeRequest(request);
    return ResponseEntity.ok().build();
  }

  @Override
  @PostMapping(value = "/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<TradeResponse> getTradeRequestHistory(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute TradeRequest request) {
    request.setMember(customUserDetails.getMember());
    TradeResponse response = tradeRequestService.getTradeRequest(request);
    response.setIsReviewWrittenByMe(tradeReviewService.isReviewWrittenByMember(
        response.getTradeRequestHistory().getTradeRequestHistoryId(), request.getMember().getMemberId()));
    return ResponseEntity.ok(tradeRequestChatRoomService.fillChatRoomIds(response));
  }

  @Override
  @PostMapping(value = "/accept", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<Void> acceptTradeRequest(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute TradeRequest request) {
    request.setMember(customUserDetails.getMember());
    tradeRequestService.completeTrade(request);
    return ResponseEntity.ok().build();
  }

  @Override
  @PostMapping(value = "/delete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<Void> cancelTradeRequest(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute TradeRequest request) {
    request.setMember(customUserDetails.getMember());
    tradeRequestService.cancelTradeRequest(request);
    return ResponseEntity.ok().build();
  }

  @Override
  @PostMapping(value = "/reject", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<Void> rejectTradeRequest(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute TradeRequest request) {
    request.setMember(customUserDetails.getMember());
    tradeRequestService.rejectTradeRequest(request);
    return ResponseEntity.ok().build();
  }
  @Override
  @PostMapping(value = "/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<Void> updateTradeRequest(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute TradeRequest request) {
    request.setMember(customUserDetails.getMember());
    tradeRequestService.updateTradeRequest(request);
    return ResponseEntity.ok().build();
  }

  @Override
  @PostMapping(value = "/get/received", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<TradeResponse> getReceivedTradeRequests(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute TradeRequest request) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(tradeRequestChatRoomService.fillChatRoomIds(
        tradeRequestService.getReceivedTradeRequests(request)));
  }

  @Override
  @PostMapping(value = "/get/sent", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<TradeResponse> getSentTradeRequests(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute TradeRequest request) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(tradeRequestChatRoomService.fillChatRoomIds(
        tradeRequestService.getSentTradeRequests(request)));
  }

  @Override
  @PostMapping(value = "/get/sent/all", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<TradeResponse> getAllSentTradeRequests(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute TradeRequest request) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(tradeRequestChatRoomService.fillChatRoomIds(
        sentTradeRequestQueryService.getAllSentTradeRequests(request)));
  }

  @Override
  @PostMapping(value = "/get/rate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<TradeResponse> getSortedTradeRate(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute TradeRequest request) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(tradeItemRecommendationService.getSortedByTradeRate(request));
  }

  @Override
  @PostMapping(value = "/get/recommend", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<TradeResponse> getAiRecommendedItems(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute TradeRequest request) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(tradeItemRecommendationService.getAiRecommendedItems(request));
  }

  @Override
  @PostMapping(value = "/review/post", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<Void> postTradeReview(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute TradeRequest request) {
    request.setMember(customUserDetails.getMember());
    tradeReviewService.postTradeReview(request);
    return ResponseEntity.ok().build();
  }

  @Override
  @PostMapping(value = "/review/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<TradeResponse> getReceivedTradeReviews(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute TradeRequest request) {
    return ResponseEntity.ok(tradeReviewService.getReceivedTradeReviews(request));
  }

  @Override
  @PostMapping(value = "/review/stats", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<TradeResponse> getReceivedTradeReviewStats(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute TradeRequest request) {
    return ResponseEntity.ok(tradeReviewService.getReceivedTradeReviewStats(request));
  }

}
