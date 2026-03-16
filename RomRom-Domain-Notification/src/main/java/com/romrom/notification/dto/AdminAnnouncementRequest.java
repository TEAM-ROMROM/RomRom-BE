package com.romrom.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.UUID;

@ToString
@AllArgsConstructor
@Getter
@Setter
@Builder
@NoArgsConstructor
public class AdminAnnouncementRequest {

  @Schema(description = "공지사항 ID (삭제 시 사용)")
  private UUID announcementId;

  @Schema(description = "공지사항 제목 (생성 시 사용)")
  private String title;

  @Schema(description = "공지사항 내용 (생성 시 사용)")
  private String content;

  @Schema(description = "페이지 번호", defaultValue = "0")
  @Builder.Default
  private Integer page = 0;

  @Schema(description = "페이지 크기", defaultValue = "20")
  @Builder.Default
  private Integer size = 20;
}
