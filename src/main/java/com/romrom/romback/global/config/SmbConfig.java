package com.romrom.romback.global.config;

import jcifs.DialectVersion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.smb.dsl.Smb;
import org.springframework.integration.smb.inbound.SmbInboundFileSynchronizer;
import org.springframework.integration.smb.session.SmbRemoteFileTemplate;
import org.springframework.integration.smb.session.SmbSessionFactory;
import org.springframework.messaging.MessageChannel;

@Configuration
public class SmbConfig {

  @Value("${smb.host}")
  private String host;

  @Value("${smb.port}")
  private int port;

  @Value("${smb.username}")
  private String username;

  @Value("${smb.password}")
  private String password;

  @Value("${smb.root-dir}")
  private String rootDir;

  @Value("${smb.dir}")
  private String dir;

  // 1. SMB 연결을 위한 SessionFactory 빈 생성
  @Bean
  public SmbSessionFactory smbSessionFactory() {
    SmbSessionFactory smbSessionFactory = new SmbSessionFactory();
    smbSessionFactory.setHost(host); // 서버 IP
    smbSessionFactory.setPort(port); // SMB 포트
    smbSessionFactory.setUsername(username); // DSM 사용자
    smbSessionFactory.setPassword(password); // DSM 비밀번호
    smbSessionFactory.setShareAndDir(rootDir);
    smbSessionFactory.setSmbMinVersion(DialectVersion.SMB210);
    smbSessionFactory.setSmbMaxVersion(DialectVersion.SMB311);
    return smbSessionFactory;
  }

  // 2. SMB 파일 전송을 위한 RemoteFileTemplate 생성 (업로드 어댑터에서 사용)
  @Bean
  public SmbRemoteFileTemplate smbRemoteFileTemplate(SmbSessionFactory smbSessionFactory) {
    return new SmbRemoteFileTemplate(smbSessionFactory);
  }

  // 3. 파일 업로드를 위한 메시지 채널 (업로드 요청은 해당 채널로 전송)
  @Bean
  public MessageChannel smbUploadChannel() {
    return new DirectChannel();
  }

  // 4. 업로드 통합 플로우
  // 클라이언트에서 "messageChannel"로 메시지를 보내면, 해당 메시지의 파일이 SMB 서버의 지정된 원격 디렉토리로 전송
  @Bean
  public IntegrationFlow smbUploadFlow(SmbRemoteFileTemplate smbRemoteFileTemplate) {
    return IntegrationFlow.from("smbUploadChannel")
        .handle(Smb.outboundAdapter(smbRemoteFileTemplate)
            // 원격 업로드할 디렉토리 지정
            .remoteDirectory(dir)
            .fileNameExpression("headers['file_name']") // 헤더에서 파일 이름 가져오기
            .autoCreateDirectory(true)) // 디렉토리가 없으면 생성
        .get();
  }

  // 5. 다운로드를 위한 인바운드 파일 동기화 설정
  // SmbInboundFileSynchronizer는 원격 SMB 서버의 파일들을 로컬 디렉토리로 동기화
  @Bean
  public SmbInboundFileSynchronizer smbInboundFileSynchronizer(SmbSessionFactory smbSessionFactory) {
    SmbInboundFileSynchronizer synchronizer = new SmbInboundFileSynchronizer(smbSessionFactory);

    // 원격 다운로드 디렉토리
    synchronizer.setRemoteDirectory(dir);
    return synchronizer;
  }

  // 6. 파일 삭제를 위한 메시지 채널
  @Bean
  public MessageChannel smbDeleteChannel() {
    return new DirectChannel();
  }

  // 7. 삭제 통합 플로우
  @Bean
  public IntegrationFlow smbDeleteFlow(SmbRemoteFileTemplate smbRemoteFileTemplate) {
    return IntegrationFlow.from("smbDeleteChannel")
        .handle(Smb.outboundAdapter(smbRemoteFileTemplate)
            .remoteDirectory(dir)
            .fileNameExpression("headers['file_name']"))
        .get();
  }
}
