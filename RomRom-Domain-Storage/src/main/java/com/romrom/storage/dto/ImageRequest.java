package com.romrom.storage.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.romrom.member.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;
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
public class ImageRequest {

  @Schema(hidden = true, description = "회원")
  @JsonIgnore
  private Member member;

  private List<MultipartFile> images;

  private List<String> imageUrls;
}
