package com.romrom.web.controller;

import com.romrom.common.dto.AdminRequest;
import com.romrom.common.dto.AdminResponse;
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

    @PostMapping(value = "/api/items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AdminResponse> handleItemsApi(AdminRequest request) {
        try {
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
                        .isSuccess(true)
                        .message("물품이 성공적으로 삭제되었습니다.")
                        .build();
                    break;
                case "detail":
                    // 물품 상세 조회 - 현재는 목록 조회와 동일하게 처리
                    response = itemService.getItemsForAdmin(request);
                    break;
                default:
                    response = AdminResponse.builder()
                        .isSuccess(false)
                        .message("지원하지 않는 액션입니다: " + request.getAction())
                        .build();
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Admin API 처리 중 오류 발생", e);
            AdminResponse errorResponse = AdminResponse.builder()
                .isSuccess(false)
                .message("서버 오류가 발생했습니다: " + e.getMessage())
                .build();
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
