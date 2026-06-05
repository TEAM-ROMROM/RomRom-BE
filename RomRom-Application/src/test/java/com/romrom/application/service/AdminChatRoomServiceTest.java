package com.romrom.application.service;

import static me.suhsaechan.suhlogger.util.SuhLogger.lineLog;
import static me.suhsaechan.suhlogger.util.SuhLogger.superLog;
import static me.suhsaechan.suhlogger.util.SuhLogger.timeLog;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.web.RomBackApplication;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = RomBackApplication.class)
@ActiveProfiles("dev")
@Slf4j
class AdminChatRoomServiceTest {

  @Autowired
  AdminChatRoomService adminChatRoomService;

  @Test
  public void mainTest() {
    lineLog("테스트시작");

    lineLog(null);
    timeLog(this::getChatRoomDetail_존재하지않는채팅방_CHATROOM_NOT_FOUND반환_테스트);
    lineLog(null);
    timeLog(this::getDeletedChatRooms_빈목록도_정상응답_테스트);
    lineLog(null);

    lineLog("테스트종료");
  }

  // 존재하지 않는 채팅방 상세 조회 시 CHATROOM_NOT_FOUND 발생 검증
  public void getChatRoomDetail_존재하지않는채팅방_CHATROOM_NOT_FOUND반환_테스트() {
    UUID nonExistentChatRoomId = UUID.randomUUID();
    AdminRequest request = AdminRequest.builder()
        .chatRoomId(nonExistentChatRoomId)
        .build();

    CustomException ex = Assertions.assertThrows(CustomException.class,
        () -> adminChatRoomService.getChatRoomDetail(request));

    superLog(ex.getErrorCode());
    Assertions.assertEquals(ErrorCode.CHATROOM_NOT_FOUND, ex.getErrorCode(),
        "존재하지 않는 채팅방 조회 시 CHATROOM_NOT_FOUND 반환해야 함");
    lineLog("getChatRoomDetail CHATROOM_NOT_FOUND 검증 완료");
  }

  // soft-delete 채팅방이 없어도(빈 목록) 정상 응답을 반환하는지 검증
  public void getDeletedChatRooms_빈목록도_정상응답_테스트() {
    // sortDirection 은 AdminRequest @Builder.Default(DESC) 가 채워주므로 생략해도 NPE 없음
    AdminRequest request = AdminRequest.builder()
        .pageNumber(0)
        .pageSize(20)
        .build();

    AdminResponse response = adminChatRoomService.getDeletedChatRooms(request);

    superLog(response.getTotalCount());
    Assertions.assertNotNull(response.getDeletedChatRooms(),
        "soft-delete 채팅방 목록은 비어있어도 null 이 아니어야 함");
    lineLog("getDeletedChatRooms 빈 목록 정상 응답 검증 완료");
  }
}
