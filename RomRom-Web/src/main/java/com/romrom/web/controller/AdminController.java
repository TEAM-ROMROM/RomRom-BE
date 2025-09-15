package com.romrom.web.controller;

import com.romrom.common.dto.AdminRequest;
import com.romrom.common.dto.AdminResponse;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.service.ItemService;
import com.romrom.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdminController {

    private final ItemService itemService;
    private final MemberService memberService;

    @PostMapping(value = "/api/dashboard", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AdminResponse> handleDashboardApi(AdminRequest request) {
        log.debug("Dashboard API 요청 받음: action={}", request.getAction());
        
        AdminResponse response;
        
        switch (request.getAction()) {
            case "recent-members":
                // 최근 가입 회원 8명 조회
                response = memberService.getRecentMembersForAdmin(8);
                break;
            case "recent-items":
                // 최근 등록 물품 4개 조회
                response = itemService.getRecentItemsForAdmin(4);
                break;
            default:
                log.error("지원하지 않는 대시보드 액션 요청: {}", request.getAction());
                throw new CustomException(ErrorCode.UNSUPPORTED_ADMIN_ACTION);
        }
        
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/api/items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AdminResponse> handleItemsApi(AdminRequest request) {
        log.debug("Admin API 요청 받음: action={}", request.getAction());
        
        AdminResponse response;
        
        switch (request.getAction()) {
            case "init":
            case "list":
                // 물품 목록 조회
                response = itemService.getItemsForAdmin(request);
                break;
            case "delete":
                // 물품 삭제
                itemService.deleteItemByAdmin(request.getItemId());
                response = AdminResponse.builder()
                    .totalCount(0L)
                    .build();
                break;
            case "detail":
                // 물품 상세 조회 - 현재는 목록 조회와 동일하게 처리
                response = itemService.getItemsForAdmin(request);
                break;
            default:
                log.error("지원하지 않는 아이템 액션 요청: {}", request.getAction());
                throw new CustomException(ErrorCode.UNSUPPORTED_ADMIN_ACTION);
        }
        
        return ResponseEntity.ok(response);
    }
}
