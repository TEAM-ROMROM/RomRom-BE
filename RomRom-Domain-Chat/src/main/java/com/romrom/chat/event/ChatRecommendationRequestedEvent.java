package com.romrom.chat.event;

import java.util.UUID;

public record ChatRecommendationRequestedEvent(
    UUID chatRoomId,
    String basedOnMessageId,
    UUID senderId,
    UUID recipientId,
    boolean recipientPresent
) {
}
