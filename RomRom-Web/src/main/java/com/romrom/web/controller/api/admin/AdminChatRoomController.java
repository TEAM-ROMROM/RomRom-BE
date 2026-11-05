package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.application.service.AdminChatModerationService;
import com.romrom.application.service.AdminChatRoomService;
import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.common.annotation.AdminAudit;
import com.romrom.common.constant.AdminAuditAction;
import com.romrom.common.constant.AdminAuditTargetType;
import lombok.RequiredArgsConstructor;
import me.suhsaechan.suhlogger.annotation.LogMonitor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 채팅방 API: 전체 목록(상태·검색), 삭제된 채팅방 목록, 상세, 내보내기, 강제 삭제
 * 운영 조치: 메시지 페이지·숨김·해제, 강제 종료, 강제 퇴장
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminChatRoomController implements AdminChatRoomControllerDocs {

    private final AdminChatRoomService adminChatRoomService;
    private final AdminChatModerationService adminChatModerationService;

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
    @AdminAudit(action = AdminAuditAction.CHAT_ROOM_EXPORT, targetType = AdminAuditTargetType.CHAT_ROOM, targetIdParameter = "chatRoomId")
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
    @AdminAudit(action = AdminAuditAction.CHAT_ROOM_FORCE_DELETE, targetType = AdminAuditTargetType.CHAT_ROOM, targetIdParameter = "chatRoomId")
    @LogMonitor
    public ResponseEntity<Void> forceDeleteChatRoom(@ModelAttribute AdminRequest request) {
        adminChatRoomService.forceDeleteChatRoom(request);
        return ResponseEntity.ok().build();
    }

    @Override
    @PostMapping(value = "/chat-rooms/list", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> searchChatRooms(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminChatRoomService.searchChatRooms(request));
    }

    @Override
    @PostMapping(value = "/chat-rooms/messages/list", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getChatMessages(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminChatModerationService.getChatMessages(request));
    }

    @Override
    @PostMapping(value = "/chat-rooms/messages/hide", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AdminAudit(action = AdminAuditAction.CHAT_MESSAGE_HIDE, targetType = AdminAuditTargetType.CHAT_MESSAGE, targetIdParameter = "chatMessageId")
    @LogMonitor
    public ResponseEntity<AdminResponse> hideChatMessage(@ModelAttribute AdminRequest request,
        @AuthenticationPrincipal CustomUserDetails principal) {
        request.setExecutorAdminId(principal.getMember().getMemberId());
        return ResponseEntity.ok(adminChatModerationService.hideChatMessage(request));
    }

    @Override
    @PostMapping(value = "/chat-rooms/messages/unhide", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AdminAudit(action = AdminAuditAction.CHAT_MESSAGE_UNHIDE, targetType = AdminAuditTargetType.CHAT_MESSAGE, targetIdParameter = "chatMessageId")
    @LogMonitor
    public ResponseEntity<AdminResponse> unhideChatMessage(@ModelAttribute AdminRequest request,
        @AuthenticationPrincipal CustomUserDetails principal) {
        request.setExecutorAdminId(principal.getMember().getMemberId());
        return ResponseEntity.ok(adminChatModerationService.unhideChatMessage(request));
    }

    @Override
    @PostMapping(value = "/chat-rooms/force-close", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AdminAudit(action = AdminAuditAction.CHAT_ROOM_FORCE_CLOSE, targetType = AdminAuditTargetType.CHAT_ROOM, targetIdParameter = "chatRoomId")
    @LogMonitor
    public ResponseEntity<AdminResponse> forceCloseChatRoom(@ModelAttribute AdminRequest request,
        @AuthenticationPrincipal CustomUserDetails principal) {
        request.setExecutorAdminId(principal.getMember().getMemberId());
        return ResponseEntity.ok(adminChatModerationService.forceCloseChatRoom(request));
    }

    @Override
    @PostMapping(value = "/chat-rooms/members/force-leave", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AdminAudit(action = AdminAuditAction.CHAT_MEMBER_FORCE_LEAVE, targetType = AdminAuditTargetType.CHAT_ROOM, targetIdParameter = "chatRoomId")
    @LogMonitor
    public ResponseEntity<AdminResponse> forceLeaveChatRoom(@ModelAttribute AdminRequest request,
        @AuthenticationPrincipal CustomUserDetails principal) {
        request.setExecutorAdminId(principal.getMember().getMemberId());
        return ResponseEntity.ok(adminChatModerationService.forceLeaveChatRoom(request));
    }
}
