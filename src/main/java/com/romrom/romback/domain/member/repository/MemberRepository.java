package com.romrom.romback.domain.member.repository;

import com.romrom.romback.domain.member.domain.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

  Optional<Member> findByUsername(String username);

  Boolean existsByUsername(String username);

}
