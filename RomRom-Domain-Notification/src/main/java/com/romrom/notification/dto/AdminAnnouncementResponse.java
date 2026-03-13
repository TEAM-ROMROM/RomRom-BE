package com.romrom.notification.dto;

import com.romrom.notification.entity.Announcement;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@ToString
@AllArgsConstructor
@Getter
@Setter
@Builder
@NoArgsConstructor
public class AdminAnnouncementResponse {

  @Schema(description = "공지사항 목록")
  private List<Announcement> announcements;

  @Schema(description = "전체 페이지 수")
  private Integer totalPages;

  @Schema(description = "전체 요소 수")
  private Long totalElements;

  @Schema(description = "현재 페이지")
  private Integer currentPage;

  @Schema(description = "처리 성공 여부")
  private Boolean success;

  @Schema(description = "처리 메시지")
  private String message;
}
