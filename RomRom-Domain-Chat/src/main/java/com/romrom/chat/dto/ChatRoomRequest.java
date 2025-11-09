package com.romrom.chat.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.romrom.common.constant.SortType;
import com.romrom.member.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.domain.Sort;

@Getter
@Setter
@Builder
@AllArgsConstructor
@ToString(exclude = "member")
public class ChatRoomRequest {

  @Schema(hidden = true, description = "회원")
  @JsonIgnore
  private Member member;

  @Schema(description = "대화 상대 회원 ID", example = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
  private UUID opponentMemberId;

  @Schema(description = "채팅방 ID", example = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
  private UUID chatRoomId;

  @Schema(description = "사용자가 채팅방에 입장했는지 여부 (true: 입장, false: 퇴장)", example = "true")
  @JsonProperty("isEntered")
  private boolean isEntered;

  @Schema(description = "거래 요청 ID", example = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
  private UUID tradeRequestHistoryId;

  @Schema(description = "페이지 번호", defaultValue = "0", example = "0")
  private int pageNumber;

  @Schema(description = "페이지 크기", defaultValue = "30", example = "30")
  private int pageSize;

  @Schema(description = "정렬 기준")
  private SortType sortType;

  @Schema(description = "정렬 방향")
  private Sort.Direction sortDirection;

  public ChatRoomRequest() {
    this.pageNumber = 0;
    this.pageSize = 30;
    this.sortType = SortType.CREATED_DATE;
    this.sortDirection = Sort.Direction.DESC;
  }
}