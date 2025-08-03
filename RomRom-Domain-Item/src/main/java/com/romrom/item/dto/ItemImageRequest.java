package com.romrom.item.dto;

import com.romrom.member.entity.Member;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.web.multipart.MultipartFile;

@ToString
@AllArgsConstructor
@Getter
@Setter
@Builder
public class ItemImageRequest {

  private Member member;
  private List<MultipartFile> itemImages;
  private List<String> itemImageUrls;
}
