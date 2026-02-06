package com.romrom.web.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

// 데이터베이스 설정
@Configuration
@EnableJpaAuditing
@EnableMongoAuditing
@EnableJpaRepositories(
    basePackages = "com.romrom",
    transactionManagerRef = "transactionManager"
)
@EnableMongoRepositories(basePackages = "com.romrom")
@EntityScan(basePackages = "com.romrom")
public class DatabaseConfig {

  @Bean
  @Primary
  public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
    return new JpaTransactionManager(entityManagerFactory);
  }
}