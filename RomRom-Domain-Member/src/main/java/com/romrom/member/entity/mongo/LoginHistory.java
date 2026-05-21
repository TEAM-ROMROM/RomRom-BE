package com.romrom.member.entity.mongo;

import com.romrom.common.constant.DeviceType;
import com.romrom.common.constant.LoginResult;
import com.romrom.common.constant.SocialPlatform;
import com.romrom.common.entity.mongo.BaseMongoEntity;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@CompoundIndexes({
    @CompoundIndex(name = "member_loginAt_idx", def = "{ 'memberId': 1, 'loginAt': -1 }")
})
public class LoginHistory extends BaseMongoEntity {

  @Id
  private String loginHistoryId;

  @Indexed
  private UUID memberId;

  @Indexed
  private LocalDateTime loginAt;

  private String ipAddress;

  private String userAgent;

  private DeviceType deviceType;

  private SocialPlatform socialPlatform;

  private LoginResult loginResult;

  private String failReason;
}
