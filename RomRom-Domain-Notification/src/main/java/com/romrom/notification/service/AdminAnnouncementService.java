package com.romrom.notification.service;

import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.notification.dto.AdminAnnouncementRequest;
import com.romrom.notification.dto.AdminAnnouncementResponse;
import com.romrom.notification.entity.Announcement;
import com.romrom.notification.event.AnnouncementEvent;
import com.romrom.notification.repository.AnnouncementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
  private final ApplicationEventPublisher eventPublisher;

  @Transactional(readOnly = true)
  public AdminAnnouncementResponse getAnnouncements(AdminAnnouncementRequest request) {
    Pageable pageable = PageRequest.of(request.getPage(), request.getSize());
    Page<Announcement> page = announcementRepository.findAllByOrderByCreatedDateDesc(pageable);

    return AdminAnnouncementResponse.builder()
        .announcements(page.getContent())
        .totalPages(page.getTotalPages())
        .totalElements(page.getTotalElements())
        .currentPage(page.getNumber())
        .build();
  }

  @Transactional
  public AdminAnnouncementResponse createAnnouncement(AdminAnnouncementRequest request) {
    Announcement announcement = Announcement.builder()
        .title(request.getTitle())
        .content(request.getContent())
        .build();

    announcementRepository.save(announcement);
    log.debug("공지사항 생성 완료: announcementId={}, title={}", announcement.getAnnouncementId(), announcement.getTitle());

    eventPublisher.publishEvent(new AnnouncementEvent(announcement.getAnnouncementId(), announcement.getTitle()));

    return AdminAnnouncementResponse.builder()
        .success(true)
        .message("공지사항이 생성되었습니다.")
        .build();
  }

  @Transactional
  public AdminAnnouncementResponse deleteAnnouncement(AdminAnnouncementRequest request) {
    Announcement announcement = announcementRepository.findByAnnouncementId(request.getAnnouncementId())
        .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));

    announcementRepository.delete(announcement);
    log.debug("공지사항 삭제 완료: announcementId={}", request.getAnnouncementId());

    return AdminAnnouncementResponse.builder()
        .success(true)
        .message("공지사항이 삭제되었습니다.")
        .build();
  }
}
