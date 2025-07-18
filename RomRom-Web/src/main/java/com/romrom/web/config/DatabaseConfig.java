package com.romrom.web.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

// 데이터베이스 설정
@Configuration
@EnableJpaAuditing
@EnableMongoAuditing
@EnableJpaRepositories(basePackages = {
    "com.romrom.member.repository",
    "com.romrom.item.repository.postgres",
    "com.romrom.common.repository",
    "com.romrom.report.repository"
})
@EnableMongoRepositories(basePackages = {
    "com.romrom.item.repository.mongo"
})
@EntityScan(basePackages = {
    "com.romrom.member.entity",
    "com.romrom.item.entity.postgres",
    "com.romrom.common.entity",
    "com.romrom.report.entity"
})
public class DatabaseConfig {
}