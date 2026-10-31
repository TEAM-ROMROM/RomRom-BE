package com.romrom.notification.service;

import com.romrom.notification.dto.HomePopupResponse;
import com.romrom.notification.repository.HomePopupRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 앱 홈 팝업 조회 (비로그인 포함): 활성 팝업 1건, 없으면 homePopup null
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HomePopupService {

  private final HomePopupRepository homePopupRepository;

  @Transactional(readOnly = true)
  public HomePopupResponse getActiveHomePopup() {
    return HomePopupResponse.builder()
        .homePopup(HomePopupSelector.selectActiveHomePopup(
            homePopupRepository.findAllByIsActiveTrue(), LocalDateTime.now()).orElse(null))
        .build();
  }
}
