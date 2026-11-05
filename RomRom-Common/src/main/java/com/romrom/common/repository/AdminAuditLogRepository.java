package com.romrom.common.repository;

import com.romrom.common.entity.postgres.AdminAuditLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 관리자 작업 기록 저장소 (#874): 목록은 Specification 조건 1회 조회 (연관 없음 > N+1 없음)
 */
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID>,
    JpaSpecificationExecutor<AdminAuditLog> {
}
