package com.romrom.romback.global.object;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GithubIssueRepository extends JpaRepository<GithubIssue, UUID> {
  Optional<GithubIssue> findByIssueNumber(Integer issueNumber);
}
