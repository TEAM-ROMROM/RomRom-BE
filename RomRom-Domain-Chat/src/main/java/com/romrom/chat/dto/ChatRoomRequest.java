package com.romrom.chat.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.romrom.common.constant.SortType;
import com.romrom.member.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
@Builder
@AllArgsConstructor
@ToString(exclude = "member")
public class ChatRoomRequest {

  // 생성자
  public ChatRoomRequest() {
    this.pageNumber = 0;
    this.pageSize = 30;
    this.sortType = SortType.CREATED_DATE;
    this.sortDirection = Sort.Direction.DESC;
  }

  @Schema(hidden = true, description = "회원")
  @JsonIgnore
  private Member member;

  @Schema(description = "대화 상대 회원 ID", example = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
  private UUID opponentMemberId;

  @Schema(description = "채팅방 ID", example = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
  private UUID chatRoomId;

  @Schema(description = "사용자가 채팅방에 입장했는지 여부 (true: 입장, false: 퇴장)", example = "true")
  private Boolean isEntered;

  @Schema(description = "거래 요청 ID", example = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
  private UUID tradeRequestHistoryId;

  @Schema(description = "물품 ID (물품별 채팅방 조회 시 사용)", example = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
  private UUID itemId;

  @Schema(description = "페이지 번호", defaultValue = "0", example = "0")
  private int pageNumber;

  @Schema(description = "페이지 크기", defaultValue = "30", example = "30")
  private int pageSize;

  @Schema(description = "정렬 기준")
  private SortType sortType;

  @Schema(description = "정렬 방향")
  private Sort.Direction sortDirection;

  @Schema(description = "채팅 메시지 ID (메시지 삭제·약속 수락·취소)", example = "6720f1c2a1b2c3d4e5f60718")
  private String chatMessageId;

  @Schema(description = "이 채팅방 알림 끄기 여부 (true: 끄기, false: 켜기)", example = "true")
  private Boolean isNotificationMuted;

  @Schema(description = "약속 일시 (KST, ISO-8601)", example = "2026-11-06T15:00:00")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private LocalDateTime appointmentAt;

  @Schema(description = "약속 장소 이름 (1~50자)", example = "군자역 7번 출구")
  private String placeName;

  @Schema(description = "약속 장소 위도 (선택, 경도와 함께)", example = "37.5571")
  private Double latitude;

  @Schema(description = "약속 장소 경도 (선택, 위도와 함께)", example = "127.0794")
  private Double longitude;

}