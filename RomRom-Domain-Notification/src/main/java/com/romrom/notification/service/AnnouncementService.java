package com.romrom.notification.service;

import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.notification.dto.AnnouncementRequest;
import com.romrom.notification.dto.AnnouncementResponse;
import com.romrom.notification.entity.Announcement;
import com.romrom.notification.repository.AnnouncementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 공지 조회: 공지 상세 (#861, 등록·수정·삭제는 AdminAnnouncementService)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AnnouncementService {

  private final AnnouncementRepository announcementRepository;

  /**
   * 공지 상세 1건: id 검증 > 조회 (없음 > ANNOUNCEMENT_NOT_FOUND)
   */
  @Transactional(readOnly = true)
  public AnnouncementResponse getAnnouncementDetail(AnnouncementRequest request) {
    if (request.getAnnouncementId() == null) {
      log.warn("[announcement detail] rejected 400 : announcementId is empty");
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    Announcement announcement = announcementRepository.findByAnnouncementId(request.getAnnouncementId())
        .orElseThrow(() -> {
          log.warn("[announcement detail] rejected 404 : announcementId={}", request.getAnnouncementId());
          return new CustomException(ErrorCode.ANNOUNCEMENT_NOT_FOUND);
        });
    return AnnouncementResponse.builder()
        .announcement(announcement)
        .build();
  }
}
