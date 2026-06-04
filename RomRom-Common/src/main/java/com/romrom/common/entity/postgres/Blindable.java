package com.romrom.common.entity.postgres;

import java.util.UUID;

/**
 * 관리자 블라인드 처리 계약
 * - BlindInfo 를 품은 엔티티가 구현하여, admin 서비스가 엔티티 종류와 무관하게
 *   동일한 흐름으로 블라인드/해제를 수행할 수 있게 한다
 */
public interface Blindable {

  /**
   * 블라인드 처리 (처리자/시각 기록)
   */
  void blind(String blindReason, UUID blindByAdminId);

  /**
   * 블라인드 해제 (처리 정보 초기화)
   */
  void unblind();
}
