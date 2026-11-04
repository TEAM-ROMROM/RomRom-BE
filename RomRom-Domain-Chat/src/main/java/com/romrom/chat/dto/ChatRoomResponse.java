package com.romrom.chat.dto;

import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.mongo.ChatUserState;
import com.romrom.chat.entity.postgres.ChatRoom;
import lombok.*;
import org.springframework.data.domain.Slice;

@ToString
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class ChatRoomResponse {
  private Boolean isOpponentDeleted;
  private ChatRoom chatRoom;
  private Slice<ChatMessage> messages;
  private Slice<ChatRoomDetailDto> chatRoomDetailDtoPage;
  private ChatUserState opponentState;
  private ChatActionRecommendationPayload latestRecommendation;
  // 메시지 조회: 내가 이 거래 후기를 썼는지 (완료 카드 후기 쓰기 버튼, 미조회 > null, #858)
  private Boolean isReviewWrittenByMe;
}
