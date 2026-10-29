package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import org.springframework.http.ResponseEntity;

public interface AdminAnnouncementControllerDocs {

    ResponseEntity<AdminResponse> getAnnouncements(AdminRequest request);

    ResponseEntity<AdminResponse> createAnnouncement(AdminRequest request);

    ResponseEntity<AdminResponse> deleteAnnouncement(AdminRequest request);
}
