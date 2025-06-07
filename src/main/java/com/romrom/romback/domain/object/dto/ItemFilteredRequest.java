package com.romrom.romback.domain.object.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
public class ItemFilteredRequest {

  public ItemFilteredRequest() {
    this.pageNumber = 0;
    this.pageSize = 10;
  }

  @Schema(defaultValue = "0")
  private Integer pageNumber;

  @Schema(defaultValue = "10")
  private Integer pageSize;
}
