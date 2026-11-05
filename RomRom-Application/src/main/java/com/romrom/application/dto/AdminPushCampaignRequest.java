package com.romrom.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.romrom.common.constant.DeviceType;
import com.romrom.common.constant.PushCampaignPurpose;
import com.romrom.common.constant.PushCampaignTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 관리자 푸시 캠페인 요청 (#874, multipart/form-data): 내용, 목적, 대상 조건, 예약 시각, 목록 페이지
 */
@ToString
@AllArgsConstructor
@Getter
@Setter
@Builder
@NoArgsConstructor
public class AdminPushCampaignRequest {

  @Schema(description = "푸시 캠페인 ID (상세·취소)")
  private UUID pushCampaignId;

  @Schema(description = "제목 (필수, 최대 100자, 광고성은 '(광고) ' 자동 부착)")
  private String pushTitle;

  @Schema(description = "본문 (필수, 최대 1000자, 광고성은 수신거부 안내 자동 부착)")
  private String pushBody;

  @Schema(description = "이미지 URL (https://, 선택)")
  private String pushImageUrl;

  @Schema(description = "알림 탭 이동 링크 (romrom:// 또는 https://, 선택)")
  private String pushDeepLink;

  @Schema(description = "목적 (INFORMATION 정보성 / ADVERTISEMENT 광고성, 미입력 INFORMATION)")
  private PushCampaignPurpose purpose;

  @Schema(description = "대상 (ALL / SEGMENT / MEMBERS / TEST)")
  private PushCampaignTargetType targetType;

  @Schema(description = "SEGMENT: 기기 종류 (ANDROID / IOS)")
  private DeviceType deviceType;

  @Schema(description = "SEGMENT: 마케팅 수신 동의 여부 (미입력 전체)")
  private Boolean isMarketingAgreed;

  @Schema(description = "SEGMENT: 최근 N일 이내 활동 (1~3650)")
  private Integer activeWithinDays;

  @Schema(description = "SEGMENT: N일 이상 미접속 (1~3650)")
  private Integer inactiveForDays;

  @Schema(description = "SEGMENT: 가입일 시작 (yyyy-MM-dd)")
  private String joinedFromDate;

  @Schema(description = "SEGMENT: 가입일 종료 (yyyy-MM-dd, 포함)")
  private String joinedToDate;

  @Schema(description = "SEGMENT: 시·도 (회원 위치 시·도 앞부분 일치, 예: 서울)")
  private String siDo;

  @Schema(description = "MEMBERS: 회원 ID·이메일·닉네임 목록 (줄바꿈·쉼표 구분, 최대 1000개)")
  private String memberIdentifiers;

  @Schema(description = "예약 발송 시각 (yyyy-MM-dd'T'HH:mm, KST, 미입력 즉시 발송)")
  private String scheduledAt;

  @Schema(description = "페이지 번호", defaultValue = "0")
  @Builder.Default
  private Integer pageNumber = 0;

  @Schema(description = "페이지 크기", defaultValue = "20")
  @Builder.Default
  private Integer pageSize = 20;

  @Schema(hidden = true)
  @JsonIgnore
  private UUID executorAdminId;
}
