package com.romrom.chat.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.romrom.chat.entity.mongo.MessageType;
import lombok.Builder;

@Builder
public record ChatMessagePayload(
    UUID roomId,
    UUID senderId,
    UUID recipientId,
    String content,
    MessageType type,
    LocalDateTime sentAt
) {}