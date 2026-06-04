package com.romrom.common.entity.postgres;

import jakarta.persistence.Embeddable;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 관리자 블라인드(비공개) 처리 정보 공통 묶음
 * - 운영자가 부적절한 콘텐츠를 숨기고 "누가·언제·왜" 처리했는지 추적하기 위한 4필드
 * - @Embeddable 이므로 품은 엔티티 테이블에 컬럼으로 펼쳐진다 (별도 테이블 X)
 * - 여러 도메인 재사용 대비해 RomRom-Common 에 위치 (이번 적용 대상은 TradeReview)
 */
@Embeddable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class BlindInfo {

  @lombok.Builder.Default
  private Boolean isBlinded = false; // 블라인드 처리 여부

  private String blindReason; // 블라인드 처리 사유

  private UUID blindByAdminId; // 블라인드 처리한 관리자 식별자

  private LocalDateTime blindDate; // 블라인드 처리 시각
}
