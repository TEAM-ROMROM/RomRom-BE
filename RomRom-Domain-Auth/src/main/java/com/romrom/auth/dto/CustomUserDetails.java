package com.romrom.auth.dto;

import com.romrom.member.entity.Member;
import java.security.Principal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
public class CustomUserDetails implements UserDetails, Principal {

  private final Member member;
  private LocalDateTime expiresAt;
  private Map<String, Object> attributes;

  public CustomUserDetails(Member member) {
    this.member = member;
  }

  public CustomUserDetails(Member member, Map<String, Object> attributes) {
    this.member = member;
    this.attributes = attributes;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return Collections.singletonList(new SimpleGrantedAuthority(member.getRole().name()));
  }

  @Override
  public String getPassword() {
    return ""; // 소셜 로그인 회원은 패스워드 미사용
  }

  @Override
  public String getUsername() {
    return member.getEmail(); // email로 username 사용
  }

  @Override
  public boolean isAccountNonExpired() {
    return member.getAccountStatus() != null && member.getAccountStatus() != null; // 필요에 따라 로직 보완
  }

  @Override
  public boolean isAccountNonLocked() {
    return member.getAccountStatus() != null && member.getAccountStatus() != null;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return member.getAccountStatus() != null && member.getAccountStatus() != null;
  }

  public String getMemberId() {
    return member.getMemberId().toString();
  }

  public void confirmExpire(long remainingMillis) {
    this.expiresAt = LocalDateTime.now().plus(remainingMillis, ChronoUnit.MILLIS);
  }

  @Override
  public String getName() {
    return member.getEmail();
  }
}
