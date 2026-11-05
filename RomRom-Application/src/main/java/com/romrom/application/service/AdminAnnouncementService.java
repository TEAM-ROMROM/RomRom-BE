package com.romrom.application.service;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.application.util.AdminLinkUrlRule;
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
import org.springframework.util.StringUtils;

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
        .imageUrl(toNullableText(request.getAnnouncementImageUrl()))
        .linkUrl(toValidatedLinkUrl(request.getAnnouncementLinkUrl()))
        .build();

    announcementRepository.save(announcement);
    log.debug("공지사항 생성 완료: announcementId={}, title={}", announcement.getAnnouncementId(), announcement.getTitle());

    // 전체 푸시 (기본 발송, false면 앱 공지 목록에만 노출)
    if (!Boolean.FALSE.equals(request.getIsPushNotificationEnabled())) {
      eventPublisher.publishEvent(new AnnouncementEvent(announcement.getAnnouncementId(), announcement.getTitle()));
    }

    return AdminResponse.builder().build();
  }

  /**
   * 공지 수정: 제목·본문 (푸시 재발송 없음)
   */
  @Transactional
  public AdminResponse updateAnnouncement(AdminRequest request) {
    Announcement announcement = announcementRepository.findByAnnouncementId(request.getAnnouncementId())
        .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
    if (!StringUtils.hasText(request.getAnnouncementTitle()) || !StringUtils.hasText(request.getAnnouncementContent())) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }

    announcement.setTitle(request.getAnnouncementTitle().trim());
    announcement.setContent(request.getAnnouncementContent().trim());
    // 이미지·링크: 보낸 경우만 반영 (공백 > 제거)
    if (request.getAnnouncementImageUrl() != null) {
      announcement.setImageUrl(toNullableText(request.getAnnouncementImageUrl()));
    }
    if (request.getAnnouncementLinkUrl() != null) {
      announcement.setLinkUrl(toValidatedLinkUrl(request.getAnnouncementLinkUrl()));
    }
    announcementRepository.save(announcement);
    log.info("[admin announcement] updated : announcementId={}", announcement.getAnnouncementId());

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

  /**
   * 링크 검증: 빈 값 > null, https:// · romrom:// 외 > INVALID_ANNOUNCEMENT_LINK_URL (#873)
   */
  private String toValidatedLinkUrl(String announcementLinkUrl) {
    String trimmedLinkUrl = toNullableText(announcementLinkUrl);
    if (trimmedLinkUrl != null && !AdminLinkUrlRule.isAllowedLinkUrl(trimmedLinkUrl)) {
      log.warn("[admin announcement] rejected 400 : link url is not https or romrom scheme");
      throw new CustomException(ErrorCode.INVALID_ANNOUNCEMENT_LINK_URL);
    }
    return trimmedLinkUrl;
  }

  // 공백 > null, 그 외 앞뒤 공백 제거
  private String toNullableText(String text) {
    return StringUtils.hasText(text) ? text.trim() : null;
  }
}
