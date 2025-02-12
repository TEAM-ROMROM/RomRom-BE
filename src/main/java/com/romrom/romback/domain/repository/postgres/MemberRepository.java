package com.romrom.romback.domain.repository.postgres;

import com.romrom.romback.domain.object.postgres.Member;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, UUID> {

  boolean existsByEmail(String email);

  Member findByEmail(String email);
}
