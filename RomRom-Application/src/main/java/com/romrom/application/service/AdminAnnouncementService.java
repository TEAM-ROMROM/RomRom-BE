package com.romrom.application.service;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
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
  public AdminResponse getAnnouncements(AdminRequest request) {
    Pageable pageable = PageRequest.of(request.getPageNumber(), request.getPageSize());
    Page<Announcement> page = announcementRepository.findAllByOrderByCreatedDateDesc(pageable);

    return AdminResponse.builder()
        .announcements(page.getContent())
        .totalPages(page.getTotalPages())
        .totalElements(page.getTotalElements())
        .currentPage(page.getNumber())
        .build();
  }

  @Transactional
  public AdminResponse createAnnouncement(AdminRequest request) {
    Announcement announcement = Announcement.builder()
        .title(request.getAnnouncementTitle())
        .content(request.getAnnouncementContent())
        .build();

    announcementRepository.save(announcement);
    log.debug("공지사항 생성 완료: announcementId={}, title={}", announcement.getAnnouncementId(), announcement.getTitle());

    eventPublisher.publishEvent(new AnnouncementEvent(announcement.getAnnouncementId(), announcement.getTitle()));

    return AdminResponse.builder().build();
  }

  @Transactional
  public AdminResponse deleteAnnouncement(AdminRequest request) {
    Announcement announcement = announcementRepository.findByAnnouncementId(request.getAnnouncementId())
        .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));

    announcementRepository.delete(announcement);
    log.debug("공지사항 삭제 완료: announcementId={}", request.getAnnouncementId());

    return AdminResponse.builder().build();
  }
}
