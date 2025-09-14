package com.romrom.web.config;

import com.romrom.auth.dto.SecurityUrls;
import com.romrom.auth.service.CustomUserDetailsService;
import com.romrom.member.repository.MemberRepository;
import com.romrom.auth.filter.TokenAuthenticationFilter;
import com.romrom.web.filter.AdminJwtAuthenticationFilter;
import com.romrom.auth.jwt.JwtUtil;
import java.util.Arrays;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtUtil jwtUtil;
  private final CustomUserDetailsService customUserDetailsService;
  private final AuthenticationConfiguration authenticationConfiguration;
  private final RedisTemplate<String, Object> redisTemplate;
  private final MemberRepository memberRepository;

  /**
   * DEPRECATED: MOBILE APP 에서 사용하지않음 : 허용된 CORS Origin 목록
   */
  private static final String[] ALLOWED_ORIGINS = {
      "http://suh-project.synology.me:8085", // 메인 API 서버
      "http://suh-project.synology.me:8086", // 테스트 API 서버
      "http://localhost:8080", // 로컬 API 서버
      "http://localhost:3000" // 로컬 웹 서버
  };

  /**
   * Security Filter Chain 설정
   */
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests((authorize) -> authorize
            .requestMatchers(SecurityUrls.AUTH_WHITELIST.toArray(new String[0]))
            .permitAll() // AUTH_WHITELIST에 등록된 URL은 인증 허용 (우선 적용)
            .requestMatchers(SecurityUrls.ADMIN_PATHS.toArray(new String[0]))
            .hasRole("ADMIN") // ADMIN_PATHS에 등록된 URL은 관리자만 접근가능
            .anyRequest().authenticated()
        )
        .logout(logout -> logout
            .logoutUrl("/logout") // "/logout" 경로로 접근 시 로그아웃
            .logoutSuccessUrl("/login") // 로그아웃 성공 후 로그인 창 이동
            .invalidateHttpSession(true)
        )
        .sessionManagement(session ->
            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        )
        .addFilterBefore(
            new TokenAuthenticationFilter(jwtUtil, customUserDetailsService),
            UsernamePasswordAuthenticationFilter.class
        )
        .addFilterBefore(
            new AdminJwtAuthenticationFilter(jwtUtil),
            TokenAuthenticationFilter.class
        )
        .build();
  }

  /**
   * 인증 메니저 설정
   */
  @Bean
  public AuthenticationManager authenticationManager(
      AuthenticationConfiguration authenticationConfiguration)
      throws Exception {

    return authenticationConfiguration.getAuthenticationManager();
  }

  /**
   * CORS 설정 소스 빈
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOriginPatterns(Collections.singletonList("*")); // 모든 Origin 허용
    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
    configuration.setAllowCredentials(true);
    configuration.setAllowedHeaders(Collections.singletonList("*"));
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  /**
   * 비밀번호 인코더 빈 (BCrypt)
   */
  @Bean
  public BCryptPasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
