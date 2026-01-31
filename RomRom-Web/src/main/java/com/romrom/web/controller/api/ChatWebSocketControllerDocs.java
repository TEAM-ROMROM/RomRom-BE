package com.romrom.web.controller.api;

import com.romrom.chat.dto.ChatMessageResponse;
import com.romrom.common.dto.Author;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;

public interface ChatWebSocketControllerDocs {
  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.02.01", author = Author.WISEUNGJAE, issueNumber = 467, description = "상대방이 채팅방을 나갔을 시, 거래요청 취소/거래완료로 간주하기 때문에 메시지 전송 불가하도록 수정"),
      @ApiChangeLog(date = "2026.01.13", author = Author.WISEUNGJAE, issueNumber = 447, description = "사진 메시지 전송 기능 추가"),
      @ApiChangeLog(date = "2026.01.03", author = Author.WISEUNGJAE, issueNumber = 428, description = "차단된 회원에게 메세지 전송을 방지하는 검증 로직 추가"),
      @ApiChangeLog(date = "2025.08.24", author = Author.WISEUNGJAE, issueNumber = 295, description = "사용자 1대1 채팅방 목록 조회 API 구현"),
  })
  @Operation(summary = "채팅 웹소켓 통신 가이드",
      description = """
            ### ❗️ 중요: 이 API는 호출용이 아닌, STOMP 웹소켓 통신 규약을 설명하기 위한 문서입니다.
            
            ---
            
            ### 1. 연결 (Connect)
            - **Endpoint**: `ws://api.romrom.xyz/chat`
            - **Header**: `Authorization: Bearer <JWT_TOKEN>`
            
            ### 2. 구독 (Subscribe)
            - **Destination**: `/sub/chat.room.{chatRoomId}`
            - 구독 성공 시, 해당 채팅방의 모든 메시지를 실시간으로 수신합니다.
            - **수신 메시지 형식 (Server → Client)은 아래 Response 예시를 참고하세요.**
            
            ### 3. 발행 (Publish)
            - **Destination**: `/app/chat.send`
            - **발행 메시지 형식 (Client → Server)은 아래 Request Body 예시를 참고하세요.**
            
            ### 일반 메시지 페이로드 설명
            - `type` 필드를 `TEXT`로 설정하고, `content` 필드에 메시지 내용을 포함시켜야 합니다.
            - imageUrls 필드는 비워둡니다.
            
            ### 사진 메시지 페이로드 설명
            - `type` 필드를 `IMAGE`로 설정하고, `imageUrls` 필드에 사진 URL List를 포함시켜야 합니다.
            - 회원이 사진과 함께 텍스트를 보내고자 할 시 content 필드에 메시지를 넣을 수 있습니다.
            - 또한 content 필드는 비워둘 수도 있습니다. 비워둘 시 "사진을 보냈습니다."로 저장 및 전송됩니다.
            """
  )
  @ApiResponse(responseCode = "200", description = "서버가 구독중인 클라이언트에게 메시지를 정상적으로 브로드캐스팅할 때의 페이로드 형식",
      content = @Content(schema = @Schema(implementation = ChatMessageResponse.class)))
  @RequestBody(description = "클라이언트가 서버로 메시지를 보낼 때의 페이로드 형식",
      content = @Content(schema = @Schema(
          example = """
                     {
                       "chatRoomId": "7d52df85-e88f-4344-bb68-a6f0dc1e03fb",
                       "content": "안녕하세요!",
                       "type": "TEXT"
                     }
                     """
      )))
  void getChatWebSocketInfo();
}
