package com.romrom.member.dto;

import com.romrom.member.entity.MemberKeywordAlert;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 키워드 알림 목록 응답 (#873): 키워드 목록(최신 등록순) + 회원당 최대 개수 (조회·등록·삭제 공통)
 */
@ToString
@AllArgsConstructor
@Getter
@Setter
@Builder
public class MemberKeywordAlertResponse {

  private final List<MemberKeywordAlert> keywordAlerts;
  private final Integer maximumKeywordAlertCount;
}
