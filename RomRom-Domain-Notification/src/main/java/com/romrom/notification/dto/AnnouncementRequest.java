package com.romrom.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 사용자 공지 상세 조회 요청 (#861)
 */
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class AnnouncementRequest {

  @Schema(description = "조회할 공지사항 ID")
  private UUID announcementId;
}
