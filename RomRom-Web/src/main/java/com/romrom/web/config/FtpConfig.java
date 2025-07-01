
package com.romrom.web.config;


import lombok.RequiredArgsConstructor;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.file.remote.gateway.AbstractRemoteFileOutboundGateway;
import org.springframework.integration.file.remote.session.CachingSessionFactory;
import org.springframework.integration.ftp.dsl.Ftp;
import org.springframework.integration.ftp.inbound.FtpInboundFileSynchronizer;
import org.springframework.integration.ftp.session.DefaultFtpSessionFactory;
import org.springframework.integration.ftp.session.FtpRemoteFileTemplate;
import org.springframework.messaging.MessageChannel;

@Configuration
@RequiredArgsConstructor
public class FtpConfig {

  @Value("${ftp.host}")
  private String host;

  @Value("${ftp.port}")
  private int port;

  @Value("${ftp.username}")
  private String username;

  @Value("${ftp.password}")
  private String password;

  @Value("${ftp.root-dir}")
  private String rootDir;

  @Value("${ftp.dir}")
  private String dir;

  @Bean
  public DefaultFtpSessionFactory ftpSessionFactory() {
    DefaultFtpSessionFactory factory = new DefaultFtpSessionFactory();
    factory.setHost(host);
    factory.setPort(port);
    factory.setUsername(username);
    factory.setPassword(password);
    factory.setClientMode(FTPClient.PASSIVE_LOCAL_DATA_CONNECTION_MODE);
    factory.setConnectTimeout(10000); // 10초
    factory.setDefaultTimeout(30000); // 30초
    factory.setDataTimeout(30000); // 30초
    return factory;
  }

  @Bean
  public CachingSessionFactory<FTPFile> cachingFtpSessionFactory() {
    CachingSessionFactory<FTPFile> factory = new CachingSessionFactory<>(ftpSessionFactory());
    factory.setPoolSize(10);        // 최대 10개 연결 유지
    factory.setSessionWaitTimeout(5000); // 연결 대기 시간 5초
    return factory;
  }

  @Bean
  public FtpRemoteFileTemplate ftpRemoteFileTemplate(DefaultFtpSessionFactory ftpSessionFactory) {
    FtpRemoteFileTemplate template = new FtpRemoteFileTemplate(ftpSessionFactory);
    template.setRemoteDirectoryExpression(new LiteralExpression("/" + rootDir + "/" + dir));
    template.setFileNameExpression(new LiteralExpression("headers['file_name']"));
    return template;
  }

  // 파일 동기화 설정
  @Bean
  public FtpInboundFileSynchronizer ftpInboundFileSynchronizer() {
    FtpInboundFileSynchronizer synchronizer = new FtpInboundFileSynchronizer(ftpSessionFactory());
    synchronizer.setDeleteRemoteFiles(false); // 다운로드 후 서버에서 삭제 여부
    synchronizer.setRemoteDirectory(dir); // "/romrom/images"
    return synchronizer;
  }

  @Bean
  public MessageChannel ftpUploadChannel() {
    return new DirectChannel();
  }

  @Bean
  public MessageChannel ftpDeleteChannel() {
    return new DirectChannel();
  }

  @Bean
  public IntegrationFlow ftpUploadFlow(FtpRemoteFileTemplate ftpRemoteFileTemplate) {
    return IntegrationFlow.from("ftpUploadChannel")
      .handle(Ftp.outboundAdapter(ftpRemoteFileTemplate)
        .remoteDirectory(dir)
        .autoCreateDirectory(true)
        .fileNameExpression("headers['file_name']")
      )
      .get();
  }

  @Bean
  public IntegrationFlow ftpDeleteFlow(FtpRemoteFileTemplate ftpRemoteFileTemplate) {
    return IntegrationFlow.from("ftpDeleteChannel")
      .handle(Ftp.outboundGateway(ftpRemoteFileTemplate, AbstractRemoteFileOutboundGateway.Command.RM)
      //  .fileNameExpression("headers['file_name']")
      )
      .get();
  }
}
