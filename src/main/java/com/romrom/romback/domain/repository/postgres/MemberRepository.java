package com.romrom.romback.domain.repository.postgres;

import com.romrom.romback.domain.object.postgres.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

  boolean existsByEmail(String email);

  Member findByEmail(String email);
}
