package com.romrom.romback.domain.member.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.romrom.romback.global.util.BasePostgresEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Member extends BasePostgresEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private Long memberId;

  private String username;

  private String password;

  private String nickname;

  private Role role;

  private AccountStatus accountStatus;
}
