package com.romrom.common.entity.postgres;

import com.romrom.common.constant.AdminAuditAction;
import com.romrom.common.constant.AdminAuditTargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * 관리자 작업 기록 (#874): 관리자, 작업 종류, 대상, 요약, 요청 파라미터 요약(비밀값 제외), IP, 시각(createdDate)
 * 테이블 원본: Flyway V1_4_78 (새 DB는 JPA 생성 > 인덱스도 아래 선언으로 생성)
 */
@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
@Table(name = "admin_audit_log", indexes = {
    @Index(name = "idx_admin_audit_log_created_date", columnList = "createdDate DESC"),
    @Index(name = "idx_admin_audit_log_admin_created_date", columnList = "adminMemberId, createdDate"),
    @Index(name = "idx_admin_audit_log_action_created_date", columnList = "action, createdDate"),
    @Index(name = "idx_admin_audit_log_target", columnList = "targetType, targetId")
})
public class AdminAuditLog extends BasePostgresEntity {

  // 요약 최대 길이
  public static final int SUMMARY_MAXIMUM_LENGTH = 200;
  // 대상 ID 최대 길이 (UUID·Mongo ObjectId)
  public static final int TARGET_ID_MAXIMUM_LENGTH = 100;
  // 클라이언트 IP 최대 길이 (IPv6 포함)
  public static final int CLIENT_IP_MAXIMUM_LENGTH = 64;

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(updatable = false, nullable = false)
  private UUID adminAuditLogId;

  private UUID adminMemberId;

  private String adminEmail;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 60)
  private AdminAuditAction action;

  @Enumerated(EnumType.STRING)
  @Column(length = 40)
  private AdminAuditTargetType targetType;

  @Column(length = TARGET_ID_MAXIMUM_LENGTH)
  private String targetId;

  @Column(length = SUMMARY_MAXIMUM_LENGTH)
  private String summary;

  // JSON 문자열 (비밀값 ***, 긴 값 잘림, 파일은 이름·크기만)
  @Column(columnDefinition = "TEXT")
  private String requestSummary;

  @Column(length = CLIENT_IP_MAXIMUM_LENGTH)
  private String clientIp;
}
