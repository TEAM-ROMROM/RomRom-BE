package com.romrom.application.service;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.application.util.AdminDateRangeParser;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.notification.entity.HomePopup;
import com.romrom.notification.repository.HomePopupRepository;
import com.romrom.storage.dto.StorageRequest;
import com.romrom.storage.service.StorageService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 관리자 홈 팝업 (#749): 목록, 등록(이미지 업로드), 수정(폼 전체 덮어쓰기·이미지 교체), 삭제
 * 이미지 업로드·삭제는 DB 트랜잭션 밖에서 실행 (업로드 동안 커넥션 점유 방지, #825)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminHomePopupService {

  // 제목 최대 길이 (HomePopup.title length와 동일)
  private static final int HOME_POPUP_TITLE_MAX_LENGTH = 100;
  // 허용 링크 형식 2개: 외부 브라우저, 앱 딥링크
  private static final List<String> ALLOWED_LINK_URL_PREFIXES = List.of("https://", "romrom://");

  private final HomePopupRepository homePopupRepository;
  private final StorageService storageService;

  public AdminResponse getHomePopups(AdminRequest request) {
    Pageable pageable = PageRequest.of(request.getPageNumber(), request.getPageSize());
    Page<HomePopup> homePopupPage = homePopupRepository.findAllByOrderByPriorityDescCreatedDateDesc(pageable);

    return AdminResponse.builder()
        .homePopups(homePopupPage.getContent())
        .totalPages(homePopupPage.getTotalPages())
        .totalElements(homePopupPage.getTotalElements())
        .currentPage(homePopupPage.getNumber())
        .build();
  }

  /**
   * 홈 팝업 등록: 입력 검증 > 이미지 업로드 > 저장
   */
  public AdminResponse createHomePopup(AdminRequest request) {
    if (!hasImageFile(request.getHomePopupImage())) {
      throw new CustomException(ErrorCode.HOME_POPUP_IMAGE_REQUIRED);
    }
    HomePopup homePopup = new HomePopup();
    applyEditableFields(request, homePopup);
    homePopup.setImageUrl(uploadHomePopupImage(request.getHomePopupImage()));

    HomePopup savedHomePopup = homePopupRepository.save(homePopup);
    log.debug("[admin home popup] created : homePopupId={}", savedHomePopup.getHomePopupId());
    return AdminResponse.builder().homePopup(savedHomePopup).build();
  }

  /**
   * 홈 팝업 수정: 조회 > 입력 검증·덮어쓰기 > (이미지 첨부 시) 업로드·교체 > 저장 > 이전 이미지 삭제
   */
  public AdminResponse updateHomePopup(AdminRequest request) {
    HomePopup homePopup = findHomePopup(request.getHomePopupId());
    applyEditableFields(request, homePopup);

    String replacedImageUrl = null;
    if (hasImageFile(request.getHomePopupImage())) {
      replacedImageUrl = homePopup.getImageUrl();
      homePopup.setImageUrl(uploadHomePopupImage(request.getHomePopupImage()));
    }

    HomePopup savedHomePopup = homePopupRepository.save(homePopup);
    // 저장 성공 후에만 이전 이미지 삭제 (실패 시 기존 팝업이 깨진 이미지를 가리키는 문제 방지)
    deleteHomePopupImageQuietly(replacedImageUrl);
    log.debug("[admin home popup] updated : homePopupId={}", savedHomePopup.getHomePopupId());
    return AdminResponse.builder().homePopup(savedHomePopup).build();
  }

  /**
   * 홈 팝업 삭제: 조회 > 삭제 > 이미지 파일 삭제 (실패는 경고만)
   */
  public AdminResponse deleteHomePopup(AdminRequest request) {
    HomePopup homePopup = findHomePopup(request.getHomePopupId());
    homePopupRepository.delete(homePopup);
    deleteHomePopupImageQuietly(homePopup.getImageUrl());
    log.debug("[admin home popup] deleted : homePopupId={}", homePopup.getHomePopupId());
    return AdminResponse.builder().build();
  }

  //-------------------------------- private 메서드 --------------------------------//

  private HomePopup findHomePopup(UUID homePopupId) {
    if (homePopupId == null) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    return homePopupRepository.findByHomePopupId(homePopupId)
        .orElseThrow(() -> new CustomException(ErrorCode.HOME_POPUP_NOT_FOUND));
  }

  /**
   * 폼 값 검증 후 적용 (등록·수정 공통, 빈 값 = 없음/제한 없음)
   * 필드 7개: 제목(필수), 설명, 링크, 우선순위(기본 0), 활성(기본 true), 시작·종료 일시
   */
  private void applyEditableFields(AdminRequest request, HomePopup homePopup) {
    String homePopupTitle = trimToNull(request.getHomePopupTitle());
    if (homePopupTitle == null || homePopupTitle.length() > HOME_POPUP_TITLE_MAX_LENGTH) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    LocalDateTime homePopupStartDate = AdminDateRangeParser.parseLocalDateTime(request.getHomePopupStartDate());
    LocalDateTime homePopupEndDate = AdminDateRangeParser.parseLocalDateTime(request.getHomePopupEndDate());
    if (homePopupStartDate != null && homePopupEndDate != null && homePopupEndDate.isBefore(homePopupStartDate)) {
      throw new CustomException(ErrorCode.INVALID_HOME_POPUP_PERIOD);
    }

    homePopup.setTitle(homePopupTitle);
    homePopup.setDescription(trimToNull(request.getHomePopupDescription()));
    homePopup.setLinkUrl(toValidatedLinkUrl(request.getHomePopupLinkUrl()));
    homePopup.setPriority(request.getHomePopupPriority() == null ? 0 : request.getHomePopupPriority());
    homePopup.setIsActive(request.getIsHomePopupActive() == null || request.getIsHomePopupActive());
    homePopup.setStartDate(homePopupStartDate);
    homePopup.setEndDate(homePopupEndDate);
  }

  /**
   * 링크 검증: 빈 값 > null, https:// · romrom:// 외 > INVALID_HOME_POPUP_LINK_URL
   */
  private String toValidatedLinkUrl(String homePopupLinkUrl) {
    String trimmedLinkUrl = trimToNull(homePopupLinkUrl);
    if (trimmedLinkUrl == null) {
      return null;
    }
    String lowerCaseLinkUrl = trimmedLinkUrl.toLowerCase(Locale.ROOT);
    boolean isAllowedLinkUrl = ALLOWED_LINK_URL_PREFIXES.stream().anyMatch(lowerCaseLinkUrl::startsWith);
    if (!isAllowedLinkUrl) {
      throw new CustomException(ErrorCode.INVALID_HOME_POPUP_LINK_URL);
    }
    return trimmedLinkUrl;
  }

  private String uploadHomePopupImage(MultipartFile homePopupImage) {
    return storageService.saveImages(StorageRequest.builder().images(List.of(homePopupImage)).build())
        .getImageUrls().get(0);
  }

  /**
   * 이미지 파일 삭제, 실패 시 경고만 (팝업 데이터 처리 결과에 영향 없음)
   */
  private void deleteHomePopupImageQuietly(String homePopupImageUrl) {
    if (homePopupImageUrl == null) {
      return;
    }
    try {
      storageService.deleteImages(StorageRequest.builder().imageUrls(List.of(homePopupImageUrl)).build());
    } catch (Exception storageDeleteException) {
      log.warn("[admin home popup] image delete failed : {}", storageDeleteException.getMessage());
    }
  }

  private static boolean hasImageFile(MultipartFile homePopupImage) {
    return homePopupImage != null && !homePopupImage.isEmpty();
  }

  private static String trimToNull(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    return text.trim();
  }
}
