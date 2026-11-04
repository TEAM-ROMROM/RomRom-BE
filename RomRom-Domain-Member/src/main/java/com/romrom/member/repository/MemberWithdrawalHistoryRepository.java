package com.romrom.member.repository;

import com.romrom.member.entity.MemberWithdrawalHistory;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberWithdrawalHistoryRepository extends JpaRepository<MemberWithdrawalHistory, UUID> {
}
