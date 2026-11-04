package com.romrom.notification.dto;

import com.romrom.notification.entity.Announcement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 사용자 공지 상세 응답: 공지 엔티티 그대로 (제목·본문·작성일·이미지·링크, #861)
 */
@ToString
@AllArgsConstructor
@Getter
@Setter
@Builder
public class AnnouncementResponse {

  private Announcement announcement;
}
