package com.romrom.member.dto;

import com.romrom.member.entity.Member;
import com.romrom.member.entity.MemberItemCategory;
import com.romrom.member.entity.MemberLocation;
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
