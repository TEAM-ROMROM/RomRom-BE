package com.romrom.application.dto;

import com.romrom.notification.entity.PushCampaign;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 관리자 푸시 캠페인 응답 (#874): 캠페인 1건·목록, 페이지, 미리보기(대상·동의 회원 수, 토큰 수, 표시 문구)
 */
@ToString
@AllArgsConstructor
@Getter
@Setter
@Builder
@NoArgsConstructor
public class AdminPushCampaignResponse {

  private PushCampaign pushCampaign;

  private List<PushCampaign> pushCampaigns;

  private Integer totalPages;

  private Long totalElements;

  private Integer currentPage;

  // 미리보기: 조건에 맞는 회원 수 (알림함 기록 대상)
  private Long targetMemberCount;

  // 미리보기: 그중 수신 동의 회원 수 (방해금지는 발송 시점 판정)
  private Long pushConsentedMemberCount;

  // 미리보기: 수신 동의 회원의 토큰 수
  private Long pushTokenCount;

  // 미리보기: 실제 표시 제목·본문 (광고 표시 문구 포함)
  private String displayTitle;

  private String displayBody;
}
