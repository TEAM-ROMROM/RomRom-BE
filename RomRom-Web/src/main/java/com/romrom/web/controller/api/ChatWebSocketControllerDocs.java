package com.romrom.web.controller.api;

import com.romrom.chat.dto.ChatMessagePayload;
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
      @ApiChangeLog(date = "2026.03.14", author = Author.WISEUNGJAE, issueNumber = 572, description = "현재 구현 기준으로 채팅 웹소켓 문서 정리"),
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
            - 메시지 이벤트 payload 는 `ChatMessagePayload` 기준입니다.
            - 주요 필드
              - `chatMessageId`: 서버가 저장한 메시지 ID
              - `chatRoomId`: 채팅방 ID
              - `senderId`: 발신자 회원 ID
              - `recipientId`: 수신자 회원 ID
              - `content`: 메시지 내용
              - `type`: `TEXT`, `IMAGE`, `SYSTEM`
              - `imageUrls`: 이미지 메시지일 때의 이미지 URL 목록
              - `createdDate`: 메시지 생성 시각
            
            ### 2-1. 메시지 읽음 이벤트 구독
            - 읽음 이벤트도 함께 수신하려면 `/sub/chat.read.{chatRoomId}` 를 추가 구독합니다.
            - 읽음 이벤트는 **클라이언트가 WebSocket으로 직접 보내는 이벤트가 아니라 서버가 발행하는 이벤트**입니다.
            - 주로 아래 상황에서 발행됩니다.
              - 사용자가 REST API `/api/chat/rooms/read-cursor/update` 를 호출해 입장/퇴장 상태를 변경했을 때
              - 새 메시지가 도착했고, 수신자가 현재 같은 채팅방 화면에 머물고 있을 때
            - 읽음 이벤트 payload 는 `ChatUserState` 기준입니다.
            - 주요 필드
              - `chatUserStateId`: 사용자 채팅방 상태 문서 ID
              - `chatRoomId`: 채팅방 ID
              - `memberId`: 상태가 변경된 사용자 ID
              - `leftAt`: 마지막 채팅방 이탈 시각. 현재 방 안에 있으면 `null`
              - `removedAt`: 채팅방 soft delete 시각. 일반적인 읽음 이벤트에서는 보통 `null`
              - `isPresent`: 현재 채팅방 화면에 머무는지 여부
            - 해석 기준
              - `isPresent = true`, `leftAt = null` 이면 현재 방 안에 있으므로 최신 메시지까지 읽은 상태로 간주합니다.
              - `isPresent = false`, `leftAt != null` 이면 `leftAt` 직전까지 읽고 나간 상태로 간주합니다.
            - 읽음 이벤트 예시 1. 상대방이 채팅방에 들어온 경우
              - 의미: 상대방이 현재 채팅방 화면에 진입한 상태
              ```json
              {
                "chatUserStateId": "67d3ef9ad8b93f49d4c50cff",
                "chatRoomId": "7d52df85-e88f-4344-bb68-a6f0dc1e03fb",
                "memberId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                "leftAt": null,
                "removedAt": null,
                "isPresent": true
              }
              ```
            - 읽음 이벤트 예시 2. 상대방이 채팅방에서 나간 경우
              - 의미: 상대방이 `2026-03-14T11:25:10` 시점에 채팅방 화면을 이탈한 상태
              ```json
              {
                "chatUserStateId": "67d3ef9ad8b93f49d4c50cff",
                "chatRoomId": "7d52df85-e88f-4344-bb68-a6f0dc1e03fb",
                "memberId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                "leftAt": "2026-03-14T11:25:10",
                "removedAt": null,
                "isPresent": false
              }
              ```
            
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
      content = @Content(schema = @Schema(implementation = ChatMessagePayload.class)))
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
