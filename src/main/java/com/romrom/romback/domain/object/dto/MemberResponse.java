package com.romrom.romback.domain.object.dto;

import com.romrom.romback.domain.object.postgres.Member;
import com.romrom.romback.domain.object.postgres.MemberItemCategory;
import com.romrom.romback.domain.object.postgres.MemberLocation;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
@AllArgsConstructor
@Getter
@Setter
@Builder
public class MemberResponse {

  private final Member member;
  private final MemberLocation memberLocation;
  private final List<MemberItemCategory> memberItemCategories;

}
