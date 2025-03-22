package com.romrom.romback.domain.object.postgres;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
public class ItemImage {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID itemImageId;

  @ManyToOne(fetch = FetchType.LAZY)
  private Item item;

  private String imageUrl; // 이미지 URL

  private String filePath; // 파일 경로

  private String originalFileName; // 원본 파일명

  private String uploadedFileName; // 업로드 파일명

  private Long fileSize; // 파일 크기
}
