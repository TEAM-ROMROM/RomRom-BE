package com.romrom.notification.repository;

import com.romrom.notification.entity.HomePopup;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HomePopupRepository extends JpaRepository<HomePopup, UUID> {

  Page<HomePopup> findAllByOrderByPriorityDescCreatedDateDesc(Pageable pageable);

  Optional<HomePopup> findByHomePopupId(UUID homePopupId);

  // 활성 팝업 후보 (관리자 등록 소량 > 기간·우선순위 선택은 HomePopupSelector)
  List<HomePopup> findAllByIsActiveTrue();
}
