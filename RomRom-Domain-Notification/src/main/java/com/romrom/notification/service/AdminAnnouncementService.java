package com.romrom.notification.service;

import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.member.entity.Member;
import com.romrom.member.service.MemberService;
import com.romrom.notification.dto.AdminAnnouncementRequest;
import com.romrom.notification.dto.AdminAnnouncementResponse;
import com.romrom.notification.entity.Announcement;
import com.romrom.notification.event.NotificationType;
import com.romrom.notification.repository.AnnouncementRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAnnouncementService {

  private final AnnouncementRepository announcementRepository;
  private final NotificationService notificationService;
  private final MemberService memberService;

  @Transactional
  public AdminAnnouncementResponse handleAction(AdminAnnouncementRequest request) {
    return switch (request.getAction()) {
      case "list" -> getAnnouncements(request);
      case "create" -> createAnnouncement(request);
      case "delete" -> deleteAnnouncement(request);
      default -> throw new CustomException(ErrorCode.INVALID_REQUEST);
    };
  }

  private AdminAnnouncementResponse getAnnouncements(AdminAnnouncementRequest request) {
    Pageable pageable = PageRequest.of(request.getPage(), request.getSize());
    Page<Announcement> page = announcementRepository.findAllByOrderByCreatedDateDesc(pageable);

    return AdminAnnouncementResponse.builder()
        .announcements(page.getContent())
        .totalPages(page.getTotalPages())
        .totalElements(page.getTotalElements())
        .currentPage(page.getNumber())
        .build();
  }

  private AdminAnnouncementResponse createAnnouncement(AdminAnnouncementRequest request) {
    Announcement announcement = Announcement.builder()
        .title(request.getTitle())
        .content(request.getContent())
        .build();

    announcementRepository.save(announcement);
    log.info("공지사항 생성 완료: announcementId={}, title={}", announcement.getAnnouncementId(), announcement.getTitle());

    // 전체 사용자 푸시 알림 전송
    sendAnnouncementToAllMembers(announcement);

    return AdminAnnouncementResponse.builder()
        .success(true)
        .message("공지사항이 생성되었으며 전체 사용자에게 알림이 전송되었습니다.")
        .build();
  }

  private AdminAnnouncementResponse deleteAnnouncement(AdminAnnouncementRequest request) {
    Announcement announcement = announcementRepository.findByAnnouncementId(request.getAnnouncementId())
        .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));

    announcementRepository.delete(announcement);
    log.info("공지사항 삭제 완료: announcementId={}", request.getAnnouncementId());

    return AdminAnnouncementResponse.builder()
        .success(true)
        .message("공지사항이 삭제되었습니다.")
        .build();
  }

  private void sendAnnouncementToAllMembers(Announcement announcement) {
    List<UUID> memberIds = memberService.getAllMembers().stream()
        .map(Member::getMemberId)
        .toList();

    if (memberIds.isEmpty()) {
      log.debug("알림을 보낼 사용자가 없습니다.");
      return;
    }

    String title = NotificationType.SYSTEM_NOTICE.getTitle();
    String body = String.format(NotificationType.SYSTEM_NOTICE.getBody(), announcement.getTitle());

    Map<String, String> payload = new HashMap<>();
    payload.put("notificationType", NotificationType.SYSTEM_NOTICE.name());
    payload.put("publishedAt", LocalDateTime.now().toString());
    payload.put("announcementId", announcement.getAnnouncementId().toString());

    log.info("전체 사용자 공지 알림 발송 시작: memberCount={}, announcementId={}", memberIds.size(), announcement.getAnnouncementId());
    notificationService.sendToMembers(memberIds, title, body, payload);
    log.info("전체 사용자 공지 알림 발송 완료: announcementId={}", announcement.getAnnouncementId());
  }
}
