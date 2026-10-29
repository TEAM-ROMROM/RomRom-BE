package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.application.service.AdminChatRoomService;
import lombok.RequiredArgsConstructor;
import me.suhsaechan.suhlogger.annotation.LogMonitor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 채팅방 API: 삭제된 채팅방 목록, 상세, 내보내기, 강제 삭제
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminChatRoomController implements AdminChatRoomControllerDocs {

    private final AdminChatRoomService adminChatRoomService;

    @Override
    @PostMapping(value = "/chat-rooms/deleted-list", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getDeletedChatRooms(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminChatRoomService.getDeletedChatRooms(request));
    }

    @Override
    @PostMapping(value = "/chat-rooms/detail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getChatRoomDetail(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminChatRoomService.getChatRoomDetail(request));
    }

    @Override
    @PostMapping(value = "/chat-rooms/export", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<Resource> exportChatRoom(@ModelAttribute AdminRequest request) {
        byte[] gzipBytes = adminChatRoomService.exportChatRoom(request);
        String downloadFileName = "chat-room_" + request.getChatRoomId() + ".json.gz";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadFileName + "\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(gzipBytes.length)
            .body(new ByteArrayResource(gzipBytes));
    }

    @Override
    @PostMapping(value = "/chat-rooms/force-delete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<Void> forceDeleteChatRoom(@ModelAttribute AdminRequest request) {
        adminChatRoomService.forceDeleteChatRoom(request);
        return ResponseEntity.ok().build();
    }
}
