package com.romrom.notification.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.romrom.common.entity.postgres.BasePostgresEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * 홈 첫 화면 팝업 (공지·이벤트, #749): 이미지·제목·설명·링크, 노출 기간·우선순위
 * 노출 1건 = isActive true + 기간 안 + priority 높은 순 (동률 최신 등록순)
 */
@Entity
@Table(indexes = @Index(name = "idx_home_popup_active_priority", columnList = "isActive, priority DESC"))
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class HomePopup extends BasePostgresEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(updatable = false, nullable = false)
  private UUID homePopupId;

  @Column(nullable = false, length = 100)
  private String title;

  @Column(columnDefinition = "TEXT")
  private String description;

  // 저장소(MinIO) 업로드 이미지 URL
  @Column(nullable = false, length = 2048)
  private String imageUrl;

  // 자세히 알아보기 링크: https:// 외부 브라우저 또는 romrom:// 앱 딥링크, 없으면 버튼 미노출
  @Column(length = 2048)
  private String linkUrl;

  // 클수록 우선 노출
  @Builder.Default
  @Column(nullable = false)
  private Integer priority = 0;

  // Boolean 래퍼 > JSON 키 isActive (primitive boolean이면 active로 직렬화)
  @Builder.Default
  @Column(nullable = false)
  private Boolean isActive = true;

  // 노출 시작 일시 (null: 제한 없음)
  private LocalDateTime startDate;

  // 노출 종료 일시 (null: 제한 없음)
  private LocalDateTime endDate;
}
