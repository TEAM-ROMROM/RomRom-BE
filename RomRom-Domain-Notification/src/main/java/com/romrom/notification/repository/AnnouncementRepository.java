package com.romrom.notification.repository;

import com.romrom.notification.entity.Announcement;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {

  Page<Announcement> findAllByOrderByCreatedDateDesc(Pageable pageable);

  Optional<Announcement> findByAnnouncementId(UUID announcementId);
}
