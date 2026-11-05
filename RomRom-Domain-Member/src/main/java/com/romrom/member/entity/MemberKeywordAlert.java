package com.romrom.member.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.romrom.common.entity.postgres.BasePostgresEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * 회원 키워드 알림 (#873): 회원, 표시용 키워드, 비교용 정규화 키워드
 * 테이블 원본: Flyway V1_4_75, 응답 키: keywordAlertId, keyword, createdDate
 */
@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true, exclude = "member")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "updatedDate"})
@Table(name = "member_keyword_alert",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_member_keyword_alert_member_normalized_keyword",
        columnNames = {"member_id", "normalized_keyword"}),
    indexes = @Index(name = "idx_member_keyword_alert_normalized_keyword", columnList = "normalized_keyword"))
public class MemberKeywordAlert extends BasePostgresEntity {

  // 키워드 컬럼 길이 (정규화 시 소문자 변환으로 길어지는 문자 대비 여유)
  public static final int KEYWORD_COLUMN_LENGTH = 50;

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(updatable = false, nullable = false)
  private UUID keywordAlertId;

  @JsonIgnore
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  // 사용자가 입력한 표기 (앞뒤·연속 공백 정리, 대소문자 유지)
  @Column(nullable = false, length = KEYWORD_COLUMN_LENGTH)
  private String keyword;

  // 중복 판정·물품 매칭 기준 (KeywordAlertRule.normalize)
  @JsonIgnore
  @Column(nullable = false, length = KEYWORD_COLUMN_LENGTH)
  private String normalizedKeyword;
}
